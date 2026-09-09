package com.attendance.service;

import com.attendance.dto.AttendanceSummary;
import com.attendance.dto.MarkAttendanceRequest;
import com.attendance.entity.Attendance;
import com.attendance.entity.MarkedBy;
import com.attendance.entity.Status;
import com.attendance.exception.DuplicateAttendanceException;
import com.attendance.repository.AttendanceRepository;
import com.fras.repository.ClassroomRepository;
import com.fras.repository.SubjectRepository;
import com.school.school_management_system.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Recording and summarising attendance.
 *
 * <p>Both write paths are now {@code @Transactional}. Neither was, and Spring
 * Data's own {@code @Transactional} on {@code save} is per call, which caused
 * two distinct problems:
 *
 * <ul>
 *   <li><b>Check-then-save was a race.</b> {@code markAttendance} asked whether
 *       a row existed and then inserted one in a separate transaction. Two
 *       requests arriving together - a teacher marking somebody the camera has
 *       just recognised, or one frame recognising the same face twice - both saw
 *       "not marked" and both inserted. The lookup is still here because it
 *       produces a good message in the ordinary case, but the guarantee now
 *       comes from the unique constraint on (student, subject, date), and the
 *       violation is translated instead of escaping as a 500.</li>
 *   <li><b>A failed register was left half written.</b>
 *       {@code markAbsenteesForSession} ran one select and one insert per
 *       student, each in its own transaction, so a failure at student twenty of
 *       forty committed nineteen absences and reported an error - leaving a
 *       register that is wrong in a way nobody can see. It is now one select,
 *       one batch insert, and one transaction that either records the whole
 *       session or none of it.</li>
 * </ul>
 */
@Service
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    // Must stay in sync with com.fras.face.FaceRecognitionService.MATCH_THRESHOLD:
    // that class already gates isRecognized() at this cosine-similarity
    // score (0.363, not a generic 0-1 "confidence"), so a face-recognition
    // request only reaches here once it already cleared that bar. This is
    // a defense-in-depth floor for a client sending a bogus/too-low score,
    // not an independent, differently-scaled threshold.
    private static final double MIN_FACE_MATCH_CONFIDENCE = 0.363;
    private static final int LATE_THRESHOLD_MINUTES = 15;

    /**
     * The statuses that count as having turned up. LATE is attendance that
     * arrived late; ABSENT is not attendance; EXCUSED is neither, so it is
     * left out of the numerator and stays in the denominator - which is the
     * same rule {@code AttendanceReports} and the client's own summary use.
     */
    private static final Set<Status> ATTENDED = EnumSet.of(Status.PRESENT, Status.LATE);

    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final ClassroomRepository classroomRepository;
    private final SubjectRepository subjectRepository;

    public AttendanceService(AttendanceRepository attendanceRepository,
                             StudentRepository studentRepository,
                             ClassroomRepository classroomRepository,
                             SubjectRepository subjectRepository) {
        this.attendanceRepository = attendanceRepository;
        this.studentRepository = studentRepository;
        this.classroomRepository = classroomRepository;
        this.subjectRepository = subjectRepository;
    }

    // =========================================================
    // WRITES
    // =========================================================

    /**
     * Records one student's attendance for one session.
     *
     * <p>The lookup and the insert are one transaction now. The lookup is kept
     * for its message - it can name the status already on record - but it is no
     * longer what makes the rule true: two concurrent requests can both pass
     * it, and the unique constraint on (student, subject, date) is what stops
     * the second. That arrives as a {@code DataIntegrityViolationException}
     * and becomes the same {@link DuplicateAttendanceException} the lookup
     * would have thrown, so a duplicate is a 409 whichever path caught it,
     * rather than a 409 from one and a 500 from the other.
     *
     * @param request      who, where, for which subject, and who is marking
     * @param sessionStart when the session began, or null if nobody said; with
     *                     no start time there is nothing to be late for
     */
    @Transactional
    public Attendance markAttendance(MarkAttendanceRequest request, LocalTime sessionStart) {
        validateRequest(request);

        requireReferencedRecords(request.getStudentId(), request.getClassroomId(), request.getSubjectId());

        LocalDate date = request.getAttendanceDate() != null
                ? request.getAttendanceDate()
                : LocalDate.now();
        LocalTime time = request.getAttendanceTime() != null
                ? request.getAttendanceTime()
                : LocalTime.now();

        attendanceRepository.findByStudentIdAndSubjectIdAndAttendanceDate(
                        request.getStudentId(), request.getSubjectId(), date)
                .ifPresent(existing -> {
                    throw new DuplicateAttendanceException("Student " + request.getStudentId()
                            + " is already recorded as " + existing.getStatus()
                            + " for subject " + request.getSubjectId() + " on " + date + ".");
                });

        Attendance attendance = new Attendance(
                request.getStudentId(),
                request.getClassroomId(),
                request.getSubjectId(),
                date,
                time,
                determineStatus(time, sessionStart),
                request.getMarkedBy(),
                request.getConfidenceScore());

        try {
            // IDENTITY ids mean Hibernate cannot defer this: the INSERT runs
            // inside save(), so the constraint answers here and not at some
            // later flush the caller has no handle on. That is what makes
            // catching it worth doing at all.
            Attendance saved = attendanceRepository.save(attendance);
            log.debug("Marked student {} as {} for subject {} on {} by {}.",
                    saved.getStudentId(), saved.getStatus(), saved.getSubjectId(),
                    saved.getAttendanceDate(), saved.getMarkedBy());
            return saved;
        } catch (DataIntegrityViolationException lostTheRace) {
            // The other request got there between the lookup and this insert.
            // Logged at info, not error: the outcome is correct - the student
            // is marked exactly once - and the caller is told so.
            log.info("Concurrent mark for student {} subject {} on {}; the first one stands.",
                    request.getStudentId(), request.getSubjectId(), date);
            throw new DuplicateAttendanceException("Student " + request.getStudentId()
                    + " was marked for subject " + request.getSubjectId() + " on " + date
                    + " by another request a moment ago.");
        }
    }

    /**
     * Closes a session: everybody on the roster who has no record for this
     * subject on this date is written as ABSENT.
     *
     * <p>One transaction, one select, one batch insert. The old version ran a
     * select and a save per student, each committing on its own, so a class of
     * forty cost forty round trips before it wrote anything and a failure at
     * student twenty left twenty absences committed, twenty missing, and an
     * error on the screen - a register that is wrong in a way nobody looking at
     * it can see. Now either the whole session is recorded or none of it is,
     * and the teacher can press the button again.
     *
     * @param allStudentIdsInClass the roster; duplicates and nulls are ignored
     * @param date                 the session date, or null for today
     * @return how many ABSENT rows were written, so the caller can say
     *         "31 marked absent" instead of "done"
     */
    @Transactional
    public int markAbsenteesForSession(List<Long> allStudentIdsInClass,
                                       Long classroomId, Long subjectId, LocalDate date) {
        if (classroomId == null || subjectId == null) {
            throw new IllegalArgumentException(
                    "classroomId and subjectId are both needed to close a session.");
        }

        LocalDate sessionDate = date != null ? date : LocalDate.now();

        // A LinkedHashSet rather than the list as given. The roster arrives as
        // a repeated query parameter, so roster=7&roster=7 used to build two
        // ABSENT rows for student 7; the second would be refused by the unique
        // constraint and - now that this is one transaction - would fail a
        // register that was otherwise perfectly fine.
        Set<Long> roster = new LinkedHashSet<>();
        if (allStudentIdsInClass != null) {
            for (Long studentId : allStudentIdsInClass) {
                if (studentId != null) {
                    roster.add(studentId);
                }
            }
        }
        List<Long> missingStudents = roster.stream()
                .filter(id -> !studentRepository.existsById(id))
                .toList();
        if (!missingStudents.isEmpty()) {
            throw new IllegalArgumentException("The roster contains student id(s) that do not exist: "
                    + missingStudents + ". Refresh the roster and try again.");
        }
        if (!classroomRepository.existsById(classroomId)) {
            throw new IllegalArgumentException("The classroom " + classroomId
                    + " no longer exists. Refresh and try again.");
        }
        if (!subjectRepository.existsById(subjectId)) {
            throw new IllegalArgumentException("The subject " + subjectId
                    + " no longer exists. Refresh and try again.");
        }

        if (roster.isEmpty()) {
            // Not an error: a session with nobody in it has nobody to mark.
            // Worth a line in the log, because the usual cause is a classroom
            // whose roster was never filled in.
            log.info("No roster given for classroom {} subject {} on {}; nothing to close.",
                    classroomId, subjectId, sessionDate);
            return 0;
        }

        // One query for the whole class. Keyed on (student, subject, date) and
        // not on classroom, deliberately: a student already marked for this
        // subject today is already marked, whichever room it happened in.
        Set<Long> alreadyMarked = new HashSet<>();
        for (Attendance existing : attendanceRepository
                .findByStudentIdInAndSubjectIdAndAttendanceDate(roster, subjectId, sessionDate)) {
            alreadyMarked.add(existing.getStudentId());
        }

        List<Attendance> absences = new ArrayList<>();
        for (Long studentId : roster) {
            if (!alreadyMarked.contains(studentId)) {
                absences.add(new Attendance(studentId, classroomId, subjectId, sessionDate,
                        null, Status.ABSENT, MarkedBy.SYSTEM_AUTO_ABSENT, null));
            }
        }

        if (absences.isEmpty()) {
            log.info("Classroom {} subject {} on {}: all {} students were already marked.",
                    classroomId, subjectId, sessionDate, roster.size());
            return 0;
        }

        try {
            attendanceRepository.saveAll(absences);
        } catch (DataIntegrityViolationException markedMeanwhile) {
            // Somebody was marked present between the select above and this
            // insert. The transaction rolls back, so the register is untouched
            // rather than half written, and the answer says what to do about it.
            log.info("Classroom {} subject {} on {} changed while it was being closed.",
                    classroomId, subjectId, sessionDate);
            throw new DuplicateAttendanceException(
                    "Somebody was marked while this session was being closed. "
                            + "Nothing was recorded - close it again to pick up the change.");
        }

        log.info("Closed classroom {} subject {} on {}: {} of {} marked absent.",
                classroomId, subjectId, sessionDate, absences.size(), roster.size());
        return absences.size();
    }

    // =========================================================
    // READS
    // =========================================================

    /**
     * Attendance percentage for one student in one subject over a date range.
     *
     * <p>{@code readOnly = true} is not only a hint to the driver: it puts both
     * counts in one transaction, so the attended figure and the total figure
     * come from the same snapshot. Counted separately - as they were - a mark
     * landing between the two queries could produce an attended count higher
     * than the total, and a percentage over 100.
     */
    @Transactional(readOnly = true)
    public AttendanceSummary getAttendanceSummary(Long studentId, Long subjectId,
                                                  LocalDate startDate, LocalDate endDate) {
        if (studentId == null || subjectId == null) {
            throw new IllegalArgumentException(
                    "studentId and subjectId are both needed for a summary.");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are both required.");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("The start date is after the end date.");
        }

        long total = attendanceRepository.countTotalSessions(studentId, subjectId, startDate, endDate);
        long attended = attendanceRepository.countAttendedSessions(
                studentId, subjectId, ATTENDED, startDate, endDate);

        return new AttendanceSummary(studentId, subjectId, total, attended);
    }

    // =========================================================
    // RULES
    // =========================================================

    /**
     * PRESENT or LATE, from the clock. With no session start time everything is
     * PRESENT: there is nothing to be late for, and inventing a start time
     * would mark people late for a session nobody scheduled.
     */
    private void requireReferencedRecords(Long studentId, Long classroomId, Long subjectId) {
        if (!studentRepository.existsById(studentId)) {
            throw new IllegalArgumentException("Student " + studentId + " does not exist. Refresh the roster and try again.");
        }
        if (!classroomRepository.existsById(classroomId)) {
            throw new IllegalArgumentException("Classroom " + classroomId + " does not exist. Refresh and try again.");
        }
        if (!subjectRepository.existsById(subjectId)) {
            throw new IllegalArgumentException("Subject " + subjectId + " does not exist. Refresh and try again.");
        }
    }

    private static Status determineStatus(LocalTime markedAt, LocalTime sessionStart) {
        if (sessionStart == null || markedAt == null) {
            return Status.PRESENT;
        }
        long minutesLate = Duration.between(sessionStart, markedAt).toMinutes();
        return minutesLate > LATE_THRESHOLD_MINUTES ? Status.LATE : Status.PRESENT;
    }

    /**
     * Checks the request before anything is written.
     *
     * <p>Every failure here is an {@code IllegalArgumentException} with a
     * sentence in it, which {@code GlobalExceptionHandler} turns into a 400
     * carrying that sentence. The alternative - letting a null id reach the
     * insert - is a not-null violation phrased in terms of a column name.
     */
    private static void validateRequest(MarkAttendanceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("No attendance details were sent.");
        }
        if (request.getStudentId() == null) {
            throw new IllegalArgumentException("studentId is required to mark attendance.");
        }
        if (request.getClassroomId() == null) {
            throw new IllegalArgumentException("classroomId is required to mark attendance.");
        }
        if (request.getSubjectId() == null) {
            throw new IllegalArgumentException("subjectId is required to mark attendance.");
        }
        if (request.getMarkedBy() == null) {
            throw new IllegalArgumentException(
                    "markedBy is required: a record has to say what marked it.");
        }
        if (request.getAttendanceDate() != null && request.getAttendanceDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Attendance cannot be recorded for a future date.");
        }

        // Only face-recognition marks carry a score, and only they are checked
        // against the floor. A teacher marking somebody by hand has no score
        // and needs none - the authority is the teacher.
        if (request.getMarkedBy() == MarkedBy.FACE_RECOGNITION) {
            Double score = request.getConfidenceScore();
            if (score == null) {
                throw new IllegalArgumentException(
                        "A face-recognition mark must carry the match score that produced it.");
            }
            if (!Double.isFinite(score) || score < MIN_FACE_MATCH_CONFIDENCE || score > 1.0) {
                throw new IllegalArgumentException("The face match score " + score
                        + " is invalid: it must be finite and between " + MIN_FACE_MATCH_CONFIDENCE
                        + " and 1.0.");
            }
        }
    }
}

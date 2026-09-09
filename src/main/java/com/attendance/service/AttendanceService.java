package com.attendance.service;

import com.attendance.dto.AttendanceSummary;
import com.attendance.dto.MarkAttendanceRequest;
import com.attendance.entity.Attendance;
import com.attendance.entity.MarkedBy;
import com.attendance.entity.Status;
import com.attendance.exception.DuplicateAttendanceException;
import com.attendance.repository.AttendanceRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;

@Service
public class AttendanceService {

    private static final double MIN_FACE_MATCH_CONFIDENCE = 0.75;
    private static final int LATE_THRESHOLD_MINUTES = 15;

    private final AttendanceRepository attendanceRepository;

    public AttendanceService(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    public Attendance markAttendance(MarkAttendanceRequest request, LocalTime sessionStartTime) {
        validateRequest(request);

        LocalDate date = request.getAttendanceDate() != null
                ? request.getAttendanceDate()
                : LocalDate.now();
        LocalTime time = request.getAttendanceTime() != null
                ? request.getAttendanceTime()
                : LocalTime.now();

        if (request.getMarkedBy() == MarkedBy.FACE_RECOGNITION
                && (request.getConfidenceScore() == null
                || request.getConfidenceScore() < MIN_FACE_MATCH_CONFIDENCE)) {
            throw new IllegalArgumentException(
                    "Face match confidence too low to auto-mark attendance: "
                            + request.getConfidenceScore());
        }

        attendanceRepository.findByStudentIdAndSubjectIdAndAttendanceDate(
                request.getStudentId(), request.getSubjectId(), date
        ).ifPresent(existing -> {
            throw new DuplicateAttendanceException(
                    "Attendance already recorded for student " + request.getStudentId()
                            + " on " + date);
        });

        Attendance attendance = new Attendance(
                request.getStudentId(),
                request.getClassroomId(),
                request.getSubjectId(),
                date,
                time,
                determineStatus(sessionStartTime, time),
                request.getMarkedBy(),
                request.getConfidenceScore()
        );

        return attendanceRepository.save(attendance);
    }

    public void markAbsenteesForSession(List<Long> allStudentIdsInClass,
                                        Long classroomId, Long subjectId, LocalDate date) {
        for (Long studentId : allStudentIdsInClass) {
            boolean alreadyMarked = attendanceRepository
                    .findByStudentIdAndSubjectIdAndAttendanceDate(studentId, subjectId, date)
                    .isPresent();

            if (!alreadyMarked) {
                Attendance absentRecord = new Attendance(
                        studentId,
                        classroomId,
                        subjectId,
                        date,
                        null,
                        Status.ABSENT,
                        MarkedBy.SYSTEM_AUTO_ABSENT,
                        null
                );
                attendanceRepository.save(absentRecord);
            }
        }
    }

    public AttendanceSummary getAttendanceSummary(Long studentId, Long subjectId,
                                                  LocalDate startDate, LocalDate endDate) {
        long total = attendanceRepository.countTotalSessions(studentId, subjectId, startDate, endDate);
        long attended = attendanceRepository.countAttendedSessions(
                studentId, subjectId, EnumSet.of(Status.PRESENT, Status.LATE), startDate, endDate);
        return new AttendanceSummary(studentId, subjectId, total, attended);
    }

    private Status determineStatus(LocalTime sessionStartTime, LocalTime markedTime) {
        if (sessionStartTime == null) {
            return Status.PRESENT;
        }
        long minutesLate = Duration.between(sessionStartTime, markedTime).toMinutes();
        return minutesLate > LATE_THRESHOLD_MINUTES ? Status.LATE : Status.PRESENT;
    }

    private void validateRequest(MarkAttendanceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.getStudentId() == null) {
            throw new IllegalArgumentException("studentId is required");
        }
        if (request.getClassroomId() == null || request.getSubjectId() == null) {
            throw new IllegalArgumentException("classroomId and subjectId are required");
        }
        if (request.getMarkedBy() == null) {
            throw new IllegalArgumentException("markedBy is required");
        }
    }
}

package com.attendance.service;

import com.attendance.dto.RegisterEntry;
import com.attendance.entity.Attendance;
import com.fras.model.Classroom;
import com.fras.model.Subject;
import com.fras.repository.ClassroomRepository;
import com.fras.repository.SubjectRepository;
import com.school.school_management_system.entity.Student;
import com.school.school_management_system.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Turns stored attendance into a register somebody can read.
 *
 * <p>{@code Attendance} holds {@code student_id}, {@code subject_id} and
 * {@code classroom_id} as plain columns with no associations - a deliberate
 * choice, because attendance is written from a camera loop that should not be
 * loading three entity graphs per face - so the names live in three other
 * tables owned by two other packages. This is the one place that joins them.
 *
 * <p>The joining is done in bulk, not per row. Resolving each line on its own
 * would issue three selects for every student in the class, which on a register
 * of sixty is a hundred and eighty queries for a single screen; instead each
 * page of lines is scanned for the ids it mentions and each table is read once.
 *
 * <p>Labels are formatted here to read exactly as the pickers above the table
 * do - "cse212 - oop", "404, Hall" - because a register that named the same
 * room differently from the dropdown that selected it would look like a
 * different room.
 */
@Service
public class RegisterView {

    private final StudentRepository students;
    private final SubjectRepository subjects;
    private final ClassroomRepository classrooms;

    public RegisterView(StudentRepository students,
                        SubjectRepository subjects,
                        ClassroomRepository classrooms) {
        this.students = students;
        this.subjects = subjects;
        this.classrooms = classrooms;
    }

    /**
     * Describes rows exactly as they were given, in the same order.
     *
     * <p>The order matters: these are the three read endpoints, and the client
     * shows the newest first because the repository sorted them that way. This
     * method adds names to lines - it does not decide which lines there are.
     */
    @Transactional(readOnly = true)
    public List<RegisterEntry> describe(List<Attendance> rows) {
        List<RegisterEntry> entries = new ArrayList<>(rows.size());
        for (Attendance row : rows) {
            entries.add(RegisterEntry.recorded(row));
        }
        return fill(entries);
    }

    /**
     * One line per student the roster expected, plus any line recorded for
     * somebody the roster did not mention.
     *
     * <p>This is what an exported register needs and the read endpoints do not:
     * a stored row proves attendance, but nothing is stored for a student who
     * never appeared, and a sheet that simply left them out would report a class
     * of sixty as a class of the forty who turned up. The absentees are the
     * reason the sheet is printed.
     *
     * <p>Rows for students outside the roster are kept rather than dropped. The
     * roster is supplied by whoever asked for the export, so leaving a marked
     * student off it would silently discard a real record - and a student
     * marked in a class they are not enrolled in is exactly the kind of thing
     * the register should show.
     */
    @Transactional(readOnly = true)
    public List<RegisterEntry> describeRoster(List<Attendance> rows, List<Long> roster,
                                             Long classroomId, Long subjectId, LocalDate date) {
        Map<Long, RegisterEntry> recorded = new LinkedHashMap<>();
        for (Attendance row : rows) {
            // First row wins. Two rows for one student in one session is a
            // duplicate, and a register that printed the student twice would
            // disagree with its own total.
            recorded.putIfAbsent(row.getStudentId(), RegisterEntry.recorded(row));
        }
        return fill(merge(recorded, roster, classroomId, subjectId, date));
    }

    /**
     * The session as one line for the top of an exported sheet: "cse212 - oop
     * in 404, Hall". Two selects, once per file, replacing a title that read
     * "Class 2 | Subject 2" - which named neither.
     */
    @Transactional(readOnly = true)
    public String describeSession(Long classroomId, Long subjectId) {
        Subject subject = subjectId == null ? null : subjects.findById(subjectId).orElse(null);
        Classroom room = classroomId == null ? null : classrooms.findById(classroomId).orElse(null);
        return subjectLabel(subject, subjectId) + " in " + roomLabel(room, classroomId);
    }

    /**
     * The roster in the order it was given, with an absence standing in
     * wherever nothing was recorded, followed by anyone marked who was not on
     * it. The roster arrives already sorted by name, because the client builds
     * it from the students list, so nothing is re-sorted here.
     */
    private static List<RegisterEntry> merge(Map<Long, RegisterEntry> recorded, List<Long> roster,
                                             Long classroomId, Long subjectId, LocalDate date) {
        List<RegisterEntry> entries = new ArrayList<>();
        Set<Long> listed = new LinkedHashSet<>();
        if (roster != null) {
            for (Long studentId : roster) {
                if (studentId == null || !listed.add(studentId)) {
                    continue;
                }
                RegisterEntry entry = recorded.get(studentId);
                entries.add(entry != null ? entry
                        : RegisterEntry.unmarked(studentId, classroomId, subjectId, date));
            }
        }
        for (Map.Entry<Long, RegisterEntry> marked : recorded.entrySet()) {
            if (!listed.contains(marked.getKey())) {
                entries.add(marked.getValue());
            }
        }
        return entries;
    }

    /**
     * Reads each table once and writes the four labels onto every line.
     *
     * <p>An id with no row behind it is not an error here. Classrooms and
     * subjects can be deleted, a roster row can be removed, and attendance
     * keeps no foreign key to any of them - so the honest answer for a missing
     * name is the id itself, which is at least true, rather than a blank cell
     * that reads like a bug.
     */
    private List<RegisterEntry> fill(List<RegisterEntry> entries) {
        Set<Long> studentIds = new LinkedHashSet<>();
        Set<Long> subjectIds = new LinkedHashSet<>();
        Set<Long> roomIds = new LinkedHashSet<>();
        for (RegisterEntry entry : entries) {
            collect(studentIds, entry.getStudentId());
            collect(subjectIds, entry.getSubjectId());
            collect(roomIds, entry.getClassroomId());
        }

        Map<Long, Student> studentsById = studentIds.isEmpty()
                ? Map.of() : byId(students.findAllById(studentIds), Student::getId);
        Map<Long, Subject> subjectsById = subjectIds.isEmpty()
                ? Map.of() : byId(subjects.findAllById(subjectIds), Subject::getId);
        Map<Long, Classroom> roomsById = roomIds.isEmpty()
                ? Map.of() : byId(classrooms.findAllById(roomIds), Classroom::getId);

        for (RegisterEntry entry : entries) {
            Student student = studentsById.get(entry.getStudentId());
            entry.setStudentName(student == null
                    ? missingStudent(entry.getStudentId())
                    : student.getName());
            // Null when the roster row is gone, which the entry renders as "-".
            entry.setSection(student == null ? null : student.getSection());
            entry.setSubject(subjectLabel(subjectsById.get(entry.getSubjectId()), entry.getSubjectId()));
            entry.setRoom(roomLabel(roomsById.get(entry.getClassroomId()), entry.getClassroomId()));
        }
        return entries;
    }

    private static void collect(Set<Long> ids, Long id) {
        if (id != null) {
            ids.add(id);
        }
    }

    private static <T> Map<Long, T> byId(List<T> rows, Function<T, Long> id) {
        Map<Long, T> byId = new HashMap<>();
        for (T row : rows) {
            byId.put(id.apply(row), row);
        }
        return byId;
    }

    /**
     * What to call a student with no roster row. The id is kept because it is
     * the only handle left on the record - it is what the attendance row stores
     * and what {@code data/faces/<id>} was enrolled under - so somebody can
     * still find out who this was.
     */
    private static String missingStudent(Long id) {
        return id == null ? "Unknown student" : "Student " + id;
    }

    /**
     * "cse212 - oop", the same as {@code SubjectRow.label()} in the client, and
     * the same as the subject picker directly above the register.
     */
    private static String subjectLabel(Subject subject, Long id) {
        if (subject == null) {
            return id == null ? "" : "Subject " + id;
        }
        String code = trimToNull(subject.getCode());
        String name = trimToNull(subject.getName());
        if (code != null && name != null) {
            return code + " - " + name;
        }
        if (code != null) {
            return code;
        }
        return name != null ? name : "Subject " + subject.getId();
    }

    /**
     * "404, Hall" when the building is recorded and "404" when it is not, which
     * is what {@code ClassroomRow.label()} and the room picker both do.
     */
    private static String roomLabel(Classroom room, Long id) {
        if (room == null) {
            return id == null ? "" : "Room " + id;
        }
        String number = trimToNull(room.getRoomNumber());
        String building = trimToNull(room.getBuilding());
        String label = number != null ? number : "Room " + room.getId();
        return building == null ? label : label + ", " + building;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

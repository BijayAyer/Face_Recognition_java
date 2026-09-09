package com.school.school_management_system.service;

import com.attendance.repository.AttendanceRepository;
import com.school.school_management_system.entity.Student;
import com.school.school_management_system.exception.EmailAlreadyExistsException;
import com.school.school_management_system.exception.RecordInUseException;
import com.school.school_management_system.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Roster operations for students.
 *
 * <p>Four things were wrong here before:
 * <ul>
 *   <li><b>Create honoured a client-supplied id.</b> {@code save()} on an
 *       entity that already carries an id is a <em>merge</em>, so
 *       {@code POST /students} with {@code "id": 4} silently overwrote
 *       student 4. The id is now cleared on create.</li>
 *   <li><b>Duplicate emails became a 500.</b> The unique constraint threw a
 *       {@code DataIntegrityViolationException} out of the flush; the email
 *       is now checked first and reported as a 409.</li>
 *   <li><b>No transactions.</b> Reads are marked read-only and writes are
 *       transactional, so a failed write cannot leave half a change behind.</li>
 *   <li><b>Deleting a student threw away their attendance.</b> Not the rows -
 *       those survived - but their meaning. {@code Attendance} keeps
 *       {@code student_id} as a plain column with no foreign key, so the delete
 *       succeeded and left every record pointing at a student who no longer
 *       existed: present in the totals, absent from every register, attributable
 *       to nobody. The delete is now refused while any attendance remains.</li>
 * </ul>
 */
@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;

    public StudentService(StudentRepository studentRepository,
                          AttendanceRepository attendanceRepository) {
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
    }

    @Transactional
    public Student saveStudent(Student student) {
        // Never let the request decide which row it is writing.
        student.setId(null);
        // Nor which account it belongs to. The controller binds the entity
        // straight from the request body, so an unfiltered user_id would let
        // one caller attach any roster row - and its attendance, and its
        // enrolled face - to their own login. It is set only by
        // AccountProfileService, from the account being created.
        student.setUserId(null);
        student.setName(trim(student.getName()));
        student.setEmail(trim(student.getEmail()));
        student.setSection(blankToNull(student.getSection()));

        if (student.getEmail() != null && studentRepository.existsByEmailIgnoreCase(student.getEmail())) {
            throw new EmailAlreadyExistsException(student.getEmail());
        }
        return studentRepository.save(student);
    }

    @Transactional(readOnly = true)
    public List<Student> getAllStudents() {
        return studentRepository.findAllByOrderByNameAsc();
    }

    /** Name-or-email substring search, used by the roster search box. */
    @Transactional(readOnly = true)
    public List<Student> search(String query) {
        if (query == null || query.isBlank()) {
            return getAllStudents();
        }
        String term = query.trim();
        return studentRepository
                .findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(term, term);
    }

    @Transactional(readOnly = true)
    public Optional<Student> getStudentById(Long id) {
        return id == null ? Optional.empty() : studentRepository.findById(id);
    }

    @Transactional
    public Optional<Student> updateStudent(Long id, Student student) {
        if (id == null) {
            return Optional.empty();
        }
        return studentRepository.findById(id).map(existing -> {
            String email = trim(student.getEmail());

            if (email != null && !email.equalsIgnoreCase(existing.getEmail())
                    && studentRepository.existsByEmailIgnoreCase(email)) {
                throw new EmailAlreadyExistsException(email);
            }

            existing.setName(trim(student.getName()));
            existing.setEmail(email);
            existing.setAge(student.getAge());
            // A PUT replaces the row, so an omitted section clears it rather
            // than being left as it was. That is the same rule the name, the
            // email and the age already follow, and the roster form always
            // sends every field - including an empty one, which is how a
            // section gets removed at all.
            existing.setSection(blankToNull(student.getSection()));
            return studentRepository.save(existing);
        });
    }

    /**
     * @return false if there is no such student, which the controller reports as
     *         a 404
     * @throws RecordInUseException if the student has attendance history, which
     *                              deleting them would orphan
     */
    @Transactional
    public boolean deleteStudent(Long id) {
        if (id == null || !studentRepository.existsById(id)) {
            return false;
        }

        long records = attendanceRepository.countByStudentId(id);
        if (records > 0) {
            throw new RecordInUseException("This student has " + records
                    + (records == 1 ? " attendance record" : " attendance records")
                    + " on file. Deleting them would leave those records with nobody"
                    + " to belong to, so the student cannot be removed.");
        }

        studentRepository.deleteById(id);
        return true;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * Null for anything with no content, so "no section" is one value in the
     * database rather than three. An empty string and a string of spaces both
     * arrive from a form field somebody tabbed through, and a register that
     * printed "" for one row and "-" for the next would be reporting a
     * difference that does not exist.
     */
    private static String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
}

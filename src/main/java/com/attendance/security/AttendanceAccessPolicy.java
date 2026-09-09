package com.attendance.security;

import com.attendance.exception.AttendanceAccessDeniedException;
import com.school.school_management_system.entity.Role;
import com.school.school_management_system.entity.Student;
import com.school.school_management_system.repository.StudentRepository;
import com.school.school_management_system.security.CustomUserDetails;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Who is allowed to read whose attendance.
 *
 * <p>This exists because the attendance endpoints were protected by
 * {@code authenticated()} and nothing else. That is enough to keep strangers
 * out, but not enough to keep signed-in accounts apart: {@code studentId} is
 * a sequential database id supplied by the caller, so any account with a
 * valid token could read every student's full attendance history - and the
 * per-subject summary, and the class register - simply by counting upwards.
 * A role check alone does not fix it either, because the accounts that must
 * be stopped are legitimately signed in; the missing check is whether the
 * record being asked for is the caller's own.
 *
 * <p>The rule applied here:
 * <ul>
 *   <li><b>ADMIN and TEACHER</b> may read any student's attendance. They are
 *       the accounts that record it in the first place, and the data model
 *       has no teacher-to-subject assignment to scope them by - inventing
 *       one here would be a guess dressed up as a security boundary.</li>
 *   <li><b>STUDENT</b> may read exactly one student's attendance: the roster
 *       row that belongs to the account they signed in with. Anything else is
 *       a 403.</li>
 * </ul>
 *
 * <p>The join is {@code students.user_id}, falling back to the email address.
 * The id is the real link and is set when the account is created; the email
 * lookup is what still matches roster rows typed in before the column
 * existed, or re-added by hand afterwards. {@code User.setEmail} lower cases
 * and trims on the way in and the lookup is case-insensitive, so the fallback
 * cannot be defeated by capitalisation.
 */
@Component
public class AttendanceAccessPolicy {

    /**
     * Refusal for a student account with nothing on the roster to point at.
     *
     * <p>Creating an account now creates the roster row too, so this is no
     * longer the ordinary state of a new sign-up - it means the row for that
     * email is already claimed by a different account, and only somebody with
     * the accounts list in front of them can untangle that. Saying who to ask
     * is the difference between a dead end and a next step.
     */
    private static final String NO_ROSTER_ROW =
            "This account is not linked to a student on the roster, so it has no attendance to"
                    + " show. Ask an administrator to check the Students page for a row with this"
                    + " email address.";

    private final StudentRepository studentRepository;

    public AttendanceAccessPolicy(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    /**
     * Throws unless the caller may read this student's attendance.
     *
     * @throws AttendanceAccessDeniedException which
     *         {@code GlobalExceptionHandler} turns into a 403 carrying this
     *         message
     */
    public void requireCanReadStudent(CustomUserDetails principal, Long studentId) {
        if (principal == null) {
            // Should be unreachable - the filter chain requires a token - but
            // an unauthenticated caller must never fall through to "allowed".
            throw new AttendanceAccessDeniedException("Sign in to read attendance records.");
        }
        if (isStaff(principal)) {
            return;
        }
        Long own = ownStudentId(principal).orElse(null);
        if (own == null) {
            throw new AttendanceAccessDeniedException(NO_ROSTER_ROW);
        }
        if (!own.equals(studentId)) {
            // Deliberately the same wording whether the id exists or not: a
            // different message for a missing student would let a caller
            // enumerate which ids are real.
            throw new AttendanceAccessDeniedException("You can only view your own attendance.");
        }
    }

    /**
     * Throws unless the caller may read a whole class register. Staff only:
     * a register lists every student in the session, so there is no version
     * of it a single student is entitled to.
     */
    public void requireCanReadRegister(CustomUserDetails principal) {
        if (principal == null || !isStaff(principal)) {
            throw new AttendanceAccessDeniedException(
                    "Only teachers and administrators can view a class register.");
        }
    }

    /** Throws unless the caller may record or amend attendance. */
    public void requireCanRecord(CustomUserDetails principal) {
        if (principal == null || !isStaff(principal)) {
            throw new AttendanceAccessDeniedException(
                    "Only teachers and administrators can record attendance.");
        }
    }

    /**
     * The roster row this login belongs to, if it belongs to one.
     *
     * <p>Tried by account id first. Only if that finds nothing does it fall
     * back to the email, because an address can be edited on either side and
     * the id cannot - so where both exist, the id is the one to trust.
     */
    public Optional<Long> ownStudentId(CustomUserDetails principal) {
        if (principal == null) {
            return Optional.empty();
        }
        Optional<Student> byAccount = principal.getId() == null
                ? Optional.empty()
                : studentRepository.findByUserId(principal.getId());
        if (byAccount.isPresent()) {
            return byAccount.map(Student::getId);
        }
        if (principal.getUsername() == null) {
            return Optional.empty();
        }
        return studentRepository.findByEmailIgnoreCase(principal.getUsername())
                .map(Student::getId);
    }

    /**
     * The caller's own roster id, or a 403 explaining why there isn't one.
     * Backs {@code GET /attendance/me}, which is the endpoint a student
     * account can use without first learning its own database id.
     *
     * <p>Staff are refused rather than given a guess: an admin's login email
     * could coincidentally match a roster row, and answering "your
     * attendance" for a teacher is a question with no meaningful answer.
     */
    public Long requireOwnStudentId(CustomUserDetails principal) {
        if (principal == null) {
            throw new AttendanceAccessDeniedException("Sign in to read attendance records.");
        }
        if (isStaff(principal)) {
            throw new AttendanceAccessDeniedException(
                    "Staff accounts have no attendance of their own. "
                            + "Ask for a specific student instead.");
        }
        return ownStudentId(principal).orElseThrow(
                () -> new AttendanceAccessDeniedException(NO_ROSTER_ROW));
    }

    private boolean isStaff(CustomUserDetails principal) {
        Role role = principal.getRole();
        return role == Role.ADMIN || role == Role.TEACHER;
    }
}

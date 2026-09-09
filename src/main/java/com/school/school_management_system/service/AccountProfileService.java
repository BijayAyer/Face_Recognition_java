package com.school.school_management_system.service;

import com.school.school_management_system.entity.Role;
import com.school.school_management_system.entity.Student;
import com.school.school_management_system.entity.Teacher;
import com.school.school_management_system.entity.User;
import com.school.school_management_system.repository.StudentRepository;
import com.school.school_management_system.repository.TeacherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Gives a new account the row that represents the person.
 *
 * <p>An account and a person were two unconnected records. Creating a login
 * wrote one row, in {@code users}, and nothing else - so a new student
 * account appeared on no roster, could not have a face enrolled (enrolment
 * needs {@code students.id} for the folder name), and answered its own
 * attendance screen with "this account is not linked to a student on the
 * roster". A new teacher account left the Teachers page just as empty. The
 * two halves existed and never met.
 *
 * <p>So on every path that creates an account - public sign-up and
 * admin-created alike - the matching row is created if it is missing, or
 * adopted if somebody had already typed it in by hand. Matching is by
 * {@code user_id} first and email second: the id is the real link, and the
 * email lookup is what adopts rows that predate the column.
 *
 * <p>Administrators get no row. An admin is neither on the roster nor
 * teaching staff, and inventing a "subject" for one would put a placeholder
 * into a list people read as fact.
 */
@Service
public class AccountProfileService {

    /**
     * What a teacher row says until an admin sets a real subject on the
     * Teachers page. The column is {@code NOT NULL} and sign-up has no field
     * to ask, so the choice is a visible placeholder or no row at all - and no
     * row is the bug being fixed.
     */
    static final String SUBJECT_UNASSIGNED = "Unassigned";

    private static final int NAME_LIMIT = 120;

    private static final Logger log = LoggerFactory.getLogger(AccountProfileService.class);

    private final StudentRepository students;
    private final TeacherRepository teachers;

    public AccountProfileService(StudentRepository students, TeacherRepository teachers) {
        this.students = students;
        this.teachers = teachers;
    }

    /**
     * What became of the account's row. Returned so the start-up repair pass
     * can report what it did in one sweep, instead of counting the gap before
     * and after and inferring.
     */
    public enum LinkResult {

        /** The role gets no row - an administrator, or no role at all. */
        NOT_APPLICABLE,

        /** Already had one. The common case on every restart. */
        ALREADY_LINKED,

        /** There was no such person yet, so the row was written. */
        CREATED,

        /** Somebody had typed the person in by hand; that row is now linked. */
        ADOPTED,

        /**
         * The row for that email belongs to a different account, so it was
         * left alone. Needs a person to resolve.
         */
        BLOCKED
    }

    /**
     * Creates or adopts the row for this account. Safe to call twice: the
     * second call finds the row it made the first time and changes nothing.
     */
    @Transactional
    public LinkResult linkOrCreate(User user) {
        if (user == null || user.getId() == null || user.getRole() == null) {
            return LinkResult.NOT_APPLICABLE;
        }
        if (user.getRole() == Role.STUDENT) {
            return linkStudent(user);
        }
        if (user.getRole() == Role.TEACHER) {
            return linkTeacher(user);
        }
        return LinkResult.NOT_APPLICABLE;
    }

    private LinkResult linkStudent(User user) {
        Student existing = students.findByUserId(user.getId())
                .or(() -> students.findByEmailIgnoreCase(user.getEmail()))
                .orElse(null);

        if (existing == null) {
            Student created = new Student();
            created.setName(displayName(user));
            created.setEmail(user.getEmail());
            // Nobody to ask at sign-up. Zero reads as "not recorded"; an admin
            // can fill it in on the Students page.
            created.setAge(0);
            created.setUserId(user.getId());
            students.save(created);
            log.info("Created roster row for student account {}.", user.getId());
            return LinkResult.CREATED;
        }
        return adopt(existing.getId(), existing.getUserId(), user, "roster", id -> {
            existing.setUserId(id);
            students.save(existing);
        });
    }

    private LinkResult linkTeacher(User user) {
        Teacher existing = teachers.findByUserId(user.getId())
                .or(() -> teachers.findByEmailIgnoreCase(user.getEmail()))
                .orElse(null);

        if (existing == null) {
            Teacher created = new Teacher();
            created.setName(displayName(user));
            created.setEmail(user.getEmail());
            created.setSubject(SUBJECT_UNASSIGNED);
            created.setUserId(user.getId());
            teachers.save(created);
            log.info("Created staff row for teacher account {}.", user.getId());
            return LinkResult.CREATED;
        }
        return adopt(existing.getId(), existing.getUserId(), user, "staff", id -> {
            existing.setUserId(id);
            teachers.save(existing);
        });
    }

    /**
     * Links a row somebody had already typed in by hand, unless it belongs to
     * a different account.
     *
     * <p>Stealing it would hand one person's attendance history, and one
     * person's enrolled face, to whoever signed up with that address second.
     * The account is left unlinked instead, which is visible and fixable; a
     * silent reassignment is neither.
     */
    private LinkResult adopt(Long rowId, Long ownerId, User user, String what, Consumer<Long> link) {
        if (ownerId == null) {
            link.accept(user.getId());
            log.info("Linked existing {} row {} to account {}.", what, rowId, user.getId());
            return LinkResult.ADOPTED;
        }
        if (Objects.equals(ownerId, user.getId())) {
            return LinkResult.ALREADY_LINKED;
        }
        log.warn("The {} row {} for that email already belongs to account {}, so account {} was"
                + " left unlinked. An administrator needs to correct one of the two addresses.",
                what, rowId, ownerId, user.getId());
        return LinkResult.BLOCKED;
    }

    /**
     * The name to write on the row. {@code User.getDisplayName()} already
     * falls back to the local part of the email when the name was left blank,
     * so the only thing added here is the column limit - {@code name} allows
     * 120 characters and an email address is allowed 180.
     */
    private static String displayName(User user) {
        String name = user.getDisplayName();
        if (name == null || name.isBlank()) {
            return "New account";
        }
        return clip(name.trim());
    }

    /** Keeps the name inside the column, whose limit is shorter than an email's. */
    private static String clip(String value) {
        return value.length() <= NAME_LIMIT ? value : value.substring(0, NAME_LIMIT);
    }
}

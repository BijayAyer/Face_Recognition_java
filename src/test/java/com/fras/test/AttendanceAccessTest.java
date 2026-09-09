package com.fras.test;

import com.school.school_management_system.entity.Role;
import com.school.school_management_system.entity.Student;
import com.school.school_management_system.entity.User;
import com.school.school_management_system.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Whose attendance a signed-in account may read.
 *
 * <p>This is the regression test for the worst defect the audit found. The
 * attendance endpoints were guarded by {@code authenticated()} and nothing
 * more, and sign-up is public, so the boundary was "anyone at all, after
 * filling in a form". {@code studentId} is a sequential database id supplied by
 * the caller, so one account could read every student's full attendance history
 * by counting upwards from 1.
 *
 * <p>A URL pattern cannot express the missing rule, because it depends on the
 * value of a path variable rather than its shape - which is why the check lives
 * in {@code AttendanceAccessPolicy}, called from the controller, and why it
 * needs a test that asks for somebody else's id specifically.
 *
 * <p>The classroom and subject ids in these fixtures are arbitrary numbers.
 * That is not laziness: {@code Attendance} stores them as plain {@code Long}
 * columns with no foreign key, so any number is as real as any other, and
 * pretending otherwise would misrepresent the schema under test.
 */
class AttendanceAccessTest extends ApiTestSupport {

    private static final LocalDate TERM_DAY = LocalDate.of(2026, 3, 4);

    @Test
    @DisplayName("a student may read their own history, by the address they signed in with")
    void aStudentCanReadTheirOwnHistory() throws Exception {
        Student mine = student("Ada Lovelace", "ada@fras.test");
        attendanceFor(mine.getId(), 1L, 1L, TERM_DAY);

        mvc.perform(get("/attendance/student/" + mine.getId())
                        .header("Authorization", bearerFor("ada@fras.test", Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentId").value(mine.getId()));
    }

    @Test
    @DisplayName("a student asking for another student's history is refused")
    void aStudentCannotReadSomebodyElsesHistory() throws Exception {
        Student mine = student("Ada Lovelace", "ada@fras.test");
        Student theirs = student("Grace Hopper", "grace@fras.test");
        attendanceFor(mine.getId(), 1L, 1L, TERM_DAY);
        attendanceFor(theirs.getId(), 1L, 1L, TERM_DAY);

        mvc.perform(get("/attendance/student/" + theirs.getId())
                        .header("Authorization", bearerFor("ada@fras.test", Role.STUDENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("your own attendance")));
    }

    /**
     * A missing id and somebody else's id are refused identically. If "no such
     * student" were reported differently, the endpoint would answer the question
     * "which ids are real?" for anyone willing to count.
     */
    @Test
    @DisplayName("an id that does not exist is refused in the same words")
    void aMissingIdLooksExactlyLikeSomebodyElses() throws Exception {
        student("Ada Lovelace", "ada@fras.test");

        mvc.perform(get("/attendance/student/999999")
                        .header("Authorization", bearerFor("ada@fras.test", Role.STUDENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("your own attendance")));
    }

    @Test
    @DisplayName("a signed-in account with no roster row is told so, not shown nothing")
    void anAccountWithNoRosterRowIsRefusedWithAReason() throws Exception {
        Student someone = student("Grace Hopper", "grace@fras.test");

        mvc.perform(get("/attendance/student/" + someone.getId())
                        .header("Authorization", bearerFor("stranger@fras.test", Role.STUDENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("not linked to a student")));
    }

    @Test
    @DisplayName("staff may read any student's history")
    void staffMayReadAnyone() throws Exception {
        Student pupil = student("Ada Lovelace", "ada@fras.test");
        attendanceFor(pupil.getId(), 1L, 1L, TERM_DAY);

        mvc.perform(get("/attendance/student/" + pupil.getId())
                        .header("Authorization", bearerFor("teacher@fras.test", Role.TEACHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(get("/attendance/student/" + pupil.getId())
                        .header("Authorization", bearerFor("head@fras.test", Role.ADMIN)))
                .andExpect(status().isOk());
    }

    /**
     * A register names every student who was in the room, so there is no subset
     * of it a single student is entitled to. This one is refused by
     * {@code SecurityConfig} before the controller is reached, which is why the
     * message is the generic one rather than the policy's own wording.
     */
    @Test
    @DisplayName("a class register is staff-only")
    void aRegisterIsStaffOnly() throws Exception {
        student("Ada Lovelace", "ada@fras.test");

        mvc.perform(get("/attendance/classroom/1/subject/1/date/" + TERM_DAY)
                        .header("Authorization", bearerFor("ada@fras.test", Role.STUDENT)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/attendance/classroom/1/subject/1/date/" + TERM_DAY)
                        .header("Authorization", bearerFor("teacher@fras.test", Role.TEACHER)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an export is staff-only")
    void anExportIsStaffOnly() throws Exception {
        student("Ada Lovelace", "ada@fras.test");

        mvc.perform(get("/attendance/export/excel/daily")
                        .param("classroomId", "1")
                        .param("subjectId", "1")
                        .param("roster", "1")
                        .header("Authorization", bearerFor("ada@fras.test", Role.STUDENT)))
                .andExpect(status().isForbidden());
    }

    /**
     * {@code /attendance/me} exists so a student account never has to learn its
     * own roster id. Staff are refused rather than guessed at: an administrator's
     * sign-in address could coincidentally match a roster row, and "your
     * attendance" is not a question a teacher account has an answer to.
     */
    @Test
    @DisplayName("/attendance/me answers for a student and refuses staff")
    void ownHistoryNeedsNoIdAndMeansNothingForStaff() throws Exception {
        Student mine = student("Ada Lovelace", "ada@fras.test");
        attendanceFor(mine.getId(), 1L, 1L, TERM_DAY);

        mvc.perform(get("/attendance/me")
                        .header("Authorization", bearerFor("ada@fras.test", Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(mine.getId()));

        mvc.perform(get("/attendance/me")
                        .header("Authorization", bearerFor("teacher@fras.test", Role.TEACHER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("no attendance of their own")));
    }

    /**
     * The account link, not the address, decides which row is "mine".
     *
     * <p>Every other test here leaves {@code user_id} unset and matches on the
     * email, which is the fallback for roster rows that predate the column.
     * This one is the case the fallback cannot answer: the roster holds a
     * different address from the one the account signs in with - a corrected
     * typo, a changed school address - and the row is still the same person's.
     * Before {@code students.user_id} existed, this student was told they were
     * "not linked to a student on the roster".
     */
    @Test
    @DisplayName("a linked roster row is found even when the addresses differ")
    void theAccountLinkOutranksTheEmail() throws Exception {
        User account = accountFor("ada@fras.test", Role.STUDENT, "test-password-1");
        Student mine = student("Ada Lovelace", "ada@school.example");
        mine.setUserId(account.getId());
        students.save(mine);
        attendanceFor(mine.getId(), 1L, 1L, TERM_DAY);

        mvc.perform(get("/attendance/me")
                        .header("Authorization", "Bearer " + jwt.generateToken(new CustomUserDetails(account))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(mine.getId()));
    }
}




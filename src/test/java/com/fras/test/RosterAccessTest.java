package com.fras.test;

import com.school.school_management_system.entity.Role;
import com.school.school_management_system.entity.Student;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may read the roster, and who may change it.
 *
 * <p>These are two different questions and the answers are deliberately
 * different, which is the whole reason this file exists.
 *
 * <p><b>Reading is staff.</b> A roster read returns every student's name and
 * email address. Sign-up is public, so guarding that behind "any signed-in
 * account" would have meant "anyone at all, after filling in a form" - and a
 * teacher genuinely needs the list, because the Enrol screen has to know which
 * id a face belongs to.
 *
 * <p><b>Writing is administrators only.</b> A roster row is no longer just a
 * name: it owns the link to a sign-in account, the id a face was enrolled
 * under, and every attendance record pointing at that id. Deleting one is not a
 * roster edit, it is a records deletion. The same verbs on {@code /teachers/**}
 * were already administrator-only, so a teacher could not touch the staff list
 * but could delete anybody from the roster - an asymmetry with no reason behind
 * it, and the client had already assumed otherwise: the Students and Teachers
 * pages are only in the rail for an administrator.
 */
class RosterAccessTest extends ApiTestSupport {

    private String admin() {
        return bearerFor("head@fras.test", Role.ADMIN);
    }

    private String teacher() {
        return bearerFor("teacher@fras.test", Role.TEACHER);
    }

    private String studentBody(String name, String email) throws Exception {
        return json.writeValueAsString(Map.of("name", name, "email", email, "age", 20));
    }

    // =========================================================
    // READING
    // =========================================================

    @Test
    @DisplayName("a teacher may read the roster, because enrolling a face needs it")
    void aTeacherMayReadTheRoster() throws Exception {
        student("Ada Lovelace", "ada@fras.test");

        mvc.perform(get("/students").header("Authorization", teacher()))
                .andExpect(status().isOk());
        mvc.perform(get("/teachers").header("Authorization", teacher()))
                .andExpect(status().isOk());
    }

    /**
     * The boundary that public sign-up puts pressure on. Anyone can obtain a
     * student token by filling in the form, so if this returned 200 the roster
     * would be a public directory of names and email addresses.
     */
    @Test
    @DisplayName("a student account cannot read the roster at all")
    void aStudentAccountCannotReadTheRoster() throws Exception {
        String pupil = bearerFor("pupil@fras.test", Role.STUDENT);

        mvc.perform(get("/students").header("Authorization", pupil))
                .andExpect(status().isForbidden());
        mvc.perform(get("/teachers").header("Authorization", pupil))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("reading the roster without a token is 401, not 403")
    void anonymousRosterReadIsUnauthorized() throws Exception {
        mvc.perform(get("/students"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================
    // WRITING
    // =========================================================

    @Test
    @DisplayName("a teacher cannot add a student, and nothing is created")
    void aTeacherCannotAddAStudent() throws Exception {
        mvc.perform(post("/students")
                        .header("Authorization", teacher())
                        .contentType("application/json")
                        .content(studentBody("Grace Hopper", "grace@fras.test")))
                .andExpect(status().isForbidden());

        assertThat(students.findByEmailIgnoreCase("grace@fras.test")).isEmpty();
    }

    @Test
    @DisplayName("a teacher cannot edit a student's details")
    void aTeacherCannotEditAStudent() throws Exception {
        Student pupil = student("Ada Lovelace", "ada@fras.test");

        mvc.perform(put("/students/" + pupil.getId())
                        .header("Authorization", teacher())
                        .contentType("application/json")
                        .content(studentBody("Someone Else", "ada@fras.test")))
                .andExpect(status().isForbidden());

        assertThat(students.findById(pupil.getId()).orElseThrow().getName())
                .isEqualTo("Ada Lovelace");
    }

    /**
     * The one that matters most. A roster row carries the id every attendance
     * record and every enrolled face is filed under, so this delete destroys
     * more than the row it names.
     */
    @Test
    @DisplayName("a teacher cannot delete a student, and the row survives")
    void aTeacherCannotDeleteAStudent() throws Exception {
        Student pupil = student("Ada Lovelace", "ada@fras.test");

        mvc.perform(delete("/students/" + pupil.getId()).header("Authorization", teacher()))
                .andExpect(status().isForbidden());

        assertThat(students.existsById(pupil.getId())).isTrue();
    }

    @Test
    @DisplayName("an admin may add, edit and delete a roster row")
    void anAdminMayChangeTheRoster() throws Exception {
        String token = admin();

        mvc.perform(post("/students")
                        .header("Authorization", token)
                        .contentType("application/json")
                        .content(studentBody("Grace Hopper", "grace@fras.test")))
                .andExpect(status().isCreated());

        Student created = students.findByEmailIgnoreCase("grace@fras.test").orElseThrow();

        mvc.perform(put("/students/" + created.getId())
                        .header("Authorization", token)
                        .contentType("application/json")
                        .content(studentBody("Grace B Hopper", "grace@fras.test")))
                .andExpect(status().isOk());

        assertThat(students.findById(created.getId()).orElseThrow().getName())
                .isEqualTo("Grace B Hopper");

        // No attendance against it, so this one is allowed through - the
        // refusal when there is history is DeleteRefusalTest's subject.
        mvc.perform(delete("/students/" + created.getId()).header("Authorization", token))
                .andExpect(status().isNoContent());

        assertThat(students.existsById(created.getId())).isFalse();
    }

    @Test
    @DisplayName("a teacher cannot add or remove a member of staff either")
    void aTeacherCannotChangeTheStaffList() throws Exception {
        String body = json.writeValueAsString(Map.of(
                "name", "Alan Turing", "email", "alan@fras.test", "subject", "Computing"));

        mvc.perform(post("/teachers")
                        .header("Authorization", teacher())
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());

        assertThat(teachers.findByEmailIgnoreCase("alan@fras.test")).isEmpty();
    }
}

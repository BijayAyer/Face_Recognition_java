package com.fras.test;

import com.school.school_management_system.entity.Role;
import com.school.school_management_system.entity.Student;
import com.school.school_management_system.entity.Teacher;
import com.school.school_management_system.entity.User;
import com.school.school_management_system.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sign-up, sign-in, and who is allowed to manage accounts.
 *
 * <p>The first two tests are the ones that matter most. Public sign-up used to
 * take {@code role} at face value, so anyone who could reach the endpoint could
 * mint themselves an administrator by adding one field to a JSON body - no
 * code, no approval, no trace. Closing that hole then introduced a quieter
 * fault: the requested role was replaced with STUDENT and the account was
 * created anyway, so choosing "Teacher" on the sign-up screen produced a
 * working student login and said nothing about it. Both are tested here,
 * because a fix for either one alone looks correct from the other's side.
 *
 * <p>The test profile leaves {@code app.security.privileged-registration-code}
 * blank, which is the shipped default and the state that has to be safe.
 */
class AuthEndpointsTest extends ApiTestSupport {

    @Test
    @DisplayName("public sign-up refuses to grant itself ADMIN, and creates nothing")
    void publicSignUpCannotGrantItselfAdmin() throws Exception {
        String body = json.writeValueAsString(Map.of(
                "email", "climber@fras.test",
                "password", "not-a-short-one",
                "fullName", "Privilege Climber",
                "role", "ADMIN"));

        mvc.perform(post("/auth/register").contentType("application/json").content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("registration code")));

        assertThat(users.findByEmailIgnoreCase("climber@fras.test")).isEmpty();
    }

    /**
     * The user-visible half of the same bug: asking for a teacher account has
     * to fail as a teacher account, not succeed as a student one.
     */
    @Test
    @DisplayName("sign-up asking for TEACHER is refused, not turned into a student")
    void publicSignUpDoesNotDowngradeATeacherToAStudent() throws Exception {
        String body = json.writeValueAsString(Map.of(
                "email", "newstaff@fras.test",
                "password", "not-a-short-one",
                "fullName", "New Staff",
                "role", "TEACHER"));

        mvc.perform(post("/auth/register").contentType("application/json").content(body))
                .andExpect(status().isForbidden());

        assertThat(users.findByEmailIgnoreCase("newstaff@fras.test")).isEmpty();
        assertThat(teachers.findByEmailIgnoreCase("newstaff@fras.test")).isEmpty();
        assertThat(students.findByEmailIgnoreCase("newstaff@fras.test")).isEmpty();
    }

    @Test
    @DisplayName("sign-up refuses a password shorter than the minimum")
    void signUpRefusesAWeakPassword() throws Exception {
        String body = json.writeValueAsString(Map.of(
                "email", "brief@fras.test",
                "password", "short"));

        mvc.perform(post("/auth/register").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("password")));

        assertThat(users.findByEmailIgnoreCase("brief@fras.test")).isEmpty();
    }

    @Test
    @DisplayName("sign-up refuses an address that is already registered")
    void signUpRefusesADuplicateAddress() throws Exception {
        accountFor("taken@fras.test", Role.STUDENT, "test-password-1");

        String body = json.writeValueAsString(Map.of(
                "email", "taken@fras.test",
                "password", "another-long-one"));

        mvc.perform(post("/auth/register").contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    /**
     * The wording is the point, not just the status. A message that said "no
     * such account" for an unknown address would turn the sign-in form into a
     * way of asking whether a particular person has one.
     */
    @Test
    @DisplayName("a wrong password and an unknown address fail the same way")
    void signInDoesNotRevealWhetherTheAddressExists() throws Exception {
        accountFor("real@fras.test", Role.STUDENT, "the-right-password");

        String wrongPassword = json.writeValueAsString(Map.of(
                "email", "real@fras.test", "password", "the-wrong-password"));
        String unknownAddress = json.writeValueAsString(Map.of(
                "email", "nobody@fras.test", "password", "the-wrong-password"));

        mvc.perform(post("/auth/login").contentType("application/json").content(wrongPassword))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", startsWith("Incorrect email or password.")));

        mvc.perform(post("/auth/login").contentType("application/json").content(unknownAddress))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", startsWith("Incorrect email or password.")));
    }

    @Test
    @DisplayName("a token from /auth/login is accepted by /auth/me")
    void signInIssuesAUsableToken() throws Exception {
        accountFor("holder@fras.test", Role.TEACHER, "the-right-password");

        String body = json.writeValueAsString(Map.of(
                "email", "holder@fras.test", "password", "the-right-password"));

        String response = mvc.perform(post("/auth/login").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andReturn().getResponse().getContentAsString();

        String token = json.readTree(response).get("token").asText();
        assertThat(token).isNotBlank();

        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("holder@fras.test"))
                .andExpect(jsonPath("$.role").value("TEACHER"));
    }

    /**
     * 401, not 403. The desktop client branches on this: 401 clears the stored
     * token and returns to the sign-in screen, 403 shows a permission message
     * and stays put. Spring Security's default entry point answers 403 when no
     * form login is configured, which left the client unable to tell that its
     * token had simply expired.
     */
    @Test
    @DisplayName("a request with no token is 401 rather than 403")
    void anonymousAccessIsUnauthorizedNotForbidden() throws Exception {
        mvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("account administration is closed to a student account")
    void accountAdministrationIsAdminOnly() throws Exception {
        mvc.perform(get("/auth/users")
                        .header("Authorization", bearerFor("pupil@fras.test", Role.STUDENT)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/auth/users")
                        .header("Authorization", bearerFor("head@fras.test", Role.ADMIN)))
                .andExpect(status().isOk());
    }

    // =========================================================
    // AN ACCOUNT AND THE PERSON IT BELONGS TO
    // =========================================================

    /**
     * Signing up used to write one row, in {@code users}, and stop. The new
     * account was on no roster, so it could not have a face enrolled - the
     * enrolment folder is named after {@code students.id} - and its own
     * attendance screen answered "this account is not linked to a student on
     * the roster".
     */
    @Test
    @DisplayName("signing up as a student puts them on the roster, linked to the account")
    void signingUpCreatesTheRosterRow() throws Exception {
        String body = json.writeValueAsString(Map.of(
                "email", "pupil@fras.test",
                "password", "not-a-short-one",
                "fullName", "Grace Hopper"));

        mvc.perform(post("/auth/register").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STUDENT"));

        User account = users.findByEmailIgnoreCase("pupil@fras.test").orElseThrow();
        Student row = students.findByUserId(account.getId()).orElseThrow();

        assertThat(row.getName()).isEqualTo("Grace Hopper");
        assertThat(row.getEmail()).isEqualTo("pupil@fras.test");
        // Sign-up has nobody to ask, and 0 reads as "not recorded" rather than
        // as a three-year-old.
        assertThat(row.getAge()).isZero();
    }

    @Test
    @DisplayName("an admin-created teacher account appears as staff")
    void adminCreatedTeacherGetsAStaffRow() throws Exception {
        String body = json.writeValueAsString(Map.of(
                "email", "staff@fras.test",
                "password", "not-a-short-one",
                "fullName", "Alan Turing",
                "role", "TEACHER"));

        mvc.perform(post("/auth/users")
                        .header("Authorization", bearerFor("head@fras.test", Role.ADMIN))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("TEACHER"));

        User account = users.findByEmailIgnoreCase("staff@fras.test").orElseThrow();
        Teacher row = teachers.findByUserId(account.getId()).orElseThrow();

        assertThat(row.getName()).isEqualTo("Alan Turing");
        assertThat(row.getSubject()).isEqualTo("Unassigned");
        // The administrator who created the account gets no row of their own.
        assertThat(students.count()).isZero();
        assertThat(teachers.count()).isEqualTo(1);
    }

    /**
     * The common case in a school that was already using the roster: the
     * student exists, then they are given a login. One person, one row - not a
     * second row and a duplicate-email 409.
     */
    @Test
    @DisplayName("signing up adopts the roster row somebody had already typed in")
    void signingUpAdoptsAnExistingRosterRow() throws Exception {
        Student typedInByHand = student("Ada Lovelace", "ada@fras.test");

        String body = json.writeValueAsString(Map.of(
                "email", "ada@fras.test",
                "password", "not-a-short-one",
                "fullName", "Ada L"));

        mvc.perform(post("/auth/register").contentType("application/json").content(body))
                .andExpect(status().isCreated());

        User account = users.findByEmailIgnoreCase("ada@fras.test").orElseThrow();

        assertThat(students.count()).isEqualTo(1);
        Student linked = students.findById(typedInByHand.getId()).orElseThrow();
        assertThat(linked.getUserId()).isEqualTo(account.getId());
        // Adopting links the row; it does not rewrite what the school entered.
        assertThat(linked.getName()).isEqualTo("Ada Lovelace");
        assertThat(linked.getAge()).isEqualTo(20);
    }

    // =========================================================
    // CHANGING A ROLE
    // =========================================================

    /**
     * The other half of refusing a privileged sign-up. Somebody has to be able
     * to say yes, and until this endpoint existed the only way to promote a new
     * sign-up was an UPDATE statement against the users table - which is not an
     * operation an administrator of a desktop application can be expected to
     * perform.
     */
    @Test
    @DisplayName("an admin can promote a student account to teacher, and the staff row appears")
    void promotingAStudentCreatesTheStaffRow() throws Exception {
        User pupil = accountFor("pupil@fras.test", Role.STUDENT, "not-a-short-one");
        rosterRowFor(pupil);
        mvc.perform(post("/auth/users/" + pupil.getId() + "/role")
                        .param("role", "TEACHER")
                        .header("Authorization", bearerFor("head@fras.test", Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"));

        assertThat(users.findById(pupil.getId()).orElseThrow().getRole()).isEqualTo(Role.TEACHER);
        assertThat(teachers.findByUserId(pupil.getId())).isPresent();
        // The roster row is kept. It owns the id a face was enrolled under and
        // every attendance record pointing at it, so a role change is not a
        // reason to delete it.
        assertThat(students.findByUserId(pupil.getId())).isPresent();
    }

    @Test
    @DisplayName("a teacher cannot change anybody's role")
    void changingARoleIsAdminOnly() throws Exception {
        User pupil = accountFor("pupil@fras.test", Role.STUDENT, "not-a-short-one");

        mvc.perform(post("/auth/users/" + pupil.getId() + "/role")
                        .param("role", "ADMIN")
                        .header("Authorization", bearerFor("teacher@fras.test", Role.TEACHER)))
                .andExpect(status().isForbidden());

        assertThat(users.findById(pupil.getId()).orElseThrow().getRole()).isEqualTo(Role.STUDENT);
    }

    /**
     * The guard that keeps the application usable. An administrator demoting the
     * last administrator - very likely themselves - would leave the accounts
     * screen unreachable by anyone, with no way back except the database.
     *
     * <p>The token is minted from the account this test created rather than
     * asked for with {@code bearerFor}, which would create a second admin under
     * the same address and defeat the "last one" the test is about.
     */
    @Test
    @DisplayName("the last enabled administrator cannot demote itself")
    void theLastAdminCannotBeDemoted() throws Exception {
        User onlyAdmin = accountFor("head@fras.test", Role.ADMIN, "not-a-short-one");

        mvc.perform(post("/auth/users/" + onlyAdmin.getId() + "/role")
                        .param("role", "TEACHER")
                        .header("Authorization",
                                "Bearer " + jwt.generateToken(new CustomUserDetails(onlyAdmin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("only enabled administrator")));

        assertThat(users.findById(onlyAdmin.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("an unknown role is refused rather than ignored")
    void anUnknownRoleIsRejected() throws Exception {
        User pupil = accountFor("pupil@fras.test", Role.STUDENT, "not-a-short-one");

        mvc.perform(post("/auth/users/" + pupil.getId() + "/role")
                        .param("role", "SUPERUSER")
                        .header("Authorization", bearerFor("head@fras.test", Role.ADMIN)))
                .andExpect(status().isBadRequest());

        assertThat(users.findById(pupil.getId()).orElseThrow().getRole()).isEqualTo(Role.STUDENT);
    }
}




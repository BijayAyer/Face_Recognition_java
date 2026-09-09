package com.fras.test;

import com.fras.model.Classroom;
import com.fras.model.Department;
import com.fras.model.Semester;
import com.fras.model.Subject;
import com.fras.model.Timetable;
import com.school.school_management_system.entity.Role;
import com.school.school_management_system.entity.Student;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deletes that used to destroy data quietly.
 *
 * <p>Two different failures are pinned down here, and only one of them was
 * visible before.
 *
 * <p><b>Where a foreign key exists</b> - a department's semesters, a semester's
 * subjects, a classroom's bookings - the database always refused the delete. The
 * problem was the sentence: a referential integrity violation reaches the user
 * as "that record is still referenced by other data", by which point the number
 * and the reason have been thrown away. So these tests assert the wording as
 * well as the 409.
 *
 * <p><b>Where no foreign key exists, nothing refused anything.</b>
 * {@code Attendance} keeps {@code student_id}, {@code subject_id} and
 * {@code classroom_id} as plain {@code Long} columns. Deleting a student, a
 * subject or a room therefore succeeded and left every attendance row for it
 * pointing at an id that named nothing - counted in totals, attributable to
 * nobody, and unreadable in every export for as long as the database lived. A
 * term's register lost as a side effect of tidying a dropdown. These are the
 * tests that matter: without the pre-check there is no mechanism at any layer
 * that would have said no.
 */
class DeleteRefusalTest extends ApiTestSupport {

    private static final LocalDate TERM_DAY = LocalDate.of(2026, 3, 4);

    private String admin() {
        return bearerFor("head@fras.test", Role.ADMIN);
    }

    // =========================================================
    // NO FOREIGN KEY: THE SILENT ONES
    // =========================================================

    @Test
    @DisplayName("a student with attendance on file cannot be deleted")
    void aStudentWithHistoryIsKept() throws Exception {
        Student pupil = student("Ada Lovelace", "ada@fras.test");
        attendanceFor(pupil.getId(), 1L, 1L, TERM_DAY);

        mvc.perform(delete("/students/" + pupil.getId()).header("Authorization", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("1 attendance record")));

        assertThat(students.existsById(pupil.getId())).isTrue();
        assertThat(attendance.countByStudentId(pupil.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("a student with no attendance is deleted normally")
    void aStudentWithNoHistoryIsRemoved() throws Exception {
        Student pupil = student("Grace Hopper", "grace@fras.test");

        mvc.perform(delete("/students/" + pupil.getId()).header("Authorization", admin()))
                .andExpect(status().isNoContent());

        assertThat(students.existsById(pupil.getId())).isFalse();
    }

    @Test
    @DisplayName("a subject with attendance against it cannot be deleted")
    void aSubjectWithHistoryIsKept() throws Exception {
        Subject taught = subject(semester(department("CS", "Computing"), "Semester 1", 1),
                "CS101", "Programming");
        attendanceFor(1L, 1L, taught.getId(), TERM_DAY);

        mvc.perform(delete("/academic/subjects/" + taught.getId()).header("Authorization", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("1 attendance record")));

        assertThat(subjects.existsById(taught.getId())).isTrue();
    }

    @Test
    @DisplayName("a room attendance has been taken in cannot be deleted")
    void aRoomWithHistoryIsKept() throws Exception {
        Classroom room = classroom("A-101");
        attendanceFor(1L, room.getId(), 1L, TERM_DAY);

        mvc.perform(delete("/academic/classrooms/" + room.getId()).header("Authorization", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("1 attendance record")));

        assertThat(classrooms.existsById(room.getId())).isTrue();
    }

    // =========================================================
    // FOREIGN KEY EXISTS: THE MESSAGE IS THE FIX
    // =========================================================

    @Test
    @DisplayName("a department that still has semesters says how many")
    void aDepartmentWithSemestersSaysHowMany() throws Exception {
        Department computing = department("CS", "Computing");
        semester(computing, "Semester 1", 1);

        mvc.perform(delete("/academic/departments/" + computing.getId()).header("Authorization", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("1 semester")));

        assertThat(departments.existsById(computing.getId())).isTrue();
    }

    @Test
    @DisplayName("a semester that still has subjects says how many")
    void aSemesterWithSubjectsSaysHowMany() throws Exception {
        Semester first = semester(department("CS", "Computing"), "Semester 1", 1);
        subject(first, "CS101", "Programming");

        mvc.perform(delete("/academic/semesters/" + first.getId()).header("Authorization", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("1 subject")));

        assertThat(semesters.existsById(first.getId())).isTrue();
    }

    @Test
    @DisplayName("a subject still on the timetable names the bookings")
    void aSubjectOnTheTimetableNamesTheBookings() throws Exception {
        Subject taught = subject(semester(department("CS", "Computing"), "Semester 1", 1),
                "CS101", "Programming");
        timetables.save(new Timetable(null, "Monday", LocalTime.of(9, 0), LocalTime.of(10, 0),
                classroom("A-101"), taught));

        mvc.perform(delete("/academic/subjects/" + taught.getId()).header("Authorization", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("1 class")));

        assertThat(subjects.existsById(taught.getId())).isTrue();
    }

    @Test
    @DisplayName("a room still booked on the timetable names the bookings")
    void aRoomOnTheTimetableNamesTheBookings() throws Exception {
        Classroom room = classroom("A-101");
        timetables.save(new Timetable(null, "Monday", LocalTime.of(9, 0), LocalTime.of(10, 0),
                room, subject(semester(department("CS", "Computing"), "Semester 1", 1),
                        "CS101", "Programming")));

        mvc.perform(delete("/academic/classrooms/" + room.getId()).header("Authorization", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("1 class")));

        assertThat(classrooms.existsById(room.getId())).isTrue();
    }

    // =========================================================
    // AND THE ORDINARY CASE STILL WORKS
    // =========================================================

    /**
     * The refusals above are worth nothing if they also block the delete a user
     * legitimately wants. An empty department goes.
     */
    @Test
    @DisplayName("a department with nothing under it is deleted")
    void anEmptyDepartmentIsRemoved() throws Exception {
        Department unused = department("XX", "Nothing Here");

        mvc.perform(delete("/academic/departments/" + unused.getId()).header("Authorization", admin()))
                .andExpect(status().isNoContent());

        assertThat(departments.existsById(unused.getId())).isFalse();
    }

    @Test
    @DisplayName("deleting academic records is closed to a teacher account")
    void academicDeletesAreAdminOnly() throws Exception {
        Department computing = department("CS", "Computing");

        mvc.perform(delete("/academic/departments/" + computing.getId())
                        .header("Authorization", bearerFor("teacher@fras.test", Role.TEACHER)))
                .andExpect(status().isForbidden());

        assertThat(departments.existsById(computing.getId())).isTrue();
    }
}




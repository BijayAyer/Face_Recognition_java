package com.fras.test;

import com.fras.model.Classroom;
import com.fras.model.Subject;
import com.fras.model.Timetable;
import com.school.school_management_system.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Double-booking a room, and the boundary that must not count as one.
 *
 * <p>The desktop client checks for a clash before it saves, but that check reads
 * the timetable and compares against the copy it just fetched - so two people
 * booking the same room in the same minute both see it free, and any caller that
 * is not this client never asks at all. The rule has to hold on the server, and
 * the tests therefore go through HTTP rather than through the client's DAO.
 *
 * <p>Half the tests here exist for the false positives. A rule that refused
 * back-to-back lectures, or the same hour in a different room, would be worse
 * than no rule: it would be wrong in the normal case, and the normal case is
 * most of the timetable. Overlap is treated as half-open - a class occupies its
 * start instant and not its end instant - and that is what the 10:00/10:00 test
 * is checking.
 */
class TimetableBookingTest extends ApiTestSupport {

    private String admin() {
        return bearerFor("head@fras.test", Role.ADMIN);
    }

    private Subject aSubject(String code) {
        return subject(semester(department("CS", "Computing"), "Semester 1", 1), code, "Programming");
    }

    private String booking(String day, String start, String end, Classroom room, Subject taught)
            throws Exception {
        return json.writeValueAsString(Map.of(
                "day", day,
                "startTime", start,
                "endTime", end,
                "classroom", Map.of("id", room.getId()),
                "subject", Map.of("id", taught.getId())));
    }

    @Test
    @DisplayName("a class overlapping one already in the room is refused, and says by what")
    void anOverlappingBookingIsRefused() throws Exception {
        Classroom room = classroom("A-101");
        Subject taught = aSubject("CS101");
        timetables.save(new Timetable(null, "Monday", LocalTime.of(9, 0), LocalTime.of(10, 0),
                room, taught));

        mvc.perform(post("/academic/timetables").header("Authorization", admin())
                        .contentType("application/json")
                        .content(booking("Monday", "09:30", "10:30", room, taught)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already booked")))
                .andExpect(jsonPath("$.message", containsString("A-101")))
                .andExpect(jsonPath("$.message", containsString("CS101")));
    }

    /**
     * The one that would be easy to get wrong. A lecture ending at 10:00 and the
     * next starting at 10:00 do not share a minute, and refusing that pair would
     * make the rule useless in the ordinary case.
     */
    @Test
    @DisplayName("back-to-back classes in the same room are allowed")
    void backToBackClassesAreAllowed() throws Exception {
        Classroom room = classroom("A-101");
        Subject taught = aSubject("CS101");
        timetables.save(new Timetable(null, "Monday", LocalTime.of(9, 0), LocalTime.of(10, 0),
                room, taught));

        mvc.perform(post("/academic/timetables").header("Authorization", admin())
                        .contentType("application/json")
                        .content(booking("Monday", "10:00", "11:00", room, taught)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("the same hour in a different room is allowed")
    void aDifferentRoomIsNotAClash() throws Exception {
        Subject taught = aSubject("CS101");
        timetables.save(new Timetable(null, "Monday", LocalTime.of(9, 0), LocalTime.of(10, 0),
                classroom("A-101"), taught));

        mvc.perform(post("/academic/timetables").header("Authorization", admin())
                        .contentType("application/json")
                        .content(booking("Monday", "09:00", "10:00", classroom("B-202"), taught)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("the same hour on a different weekday is allowed")
    void aDifferentDayIsNotAClash() throws Exception {
        Classroom room = classroom("A-101");
        Subject taught = aSubject("CS101");
        timetables.save(new Timetable(null, "Monday", LocalTime.of(9, 0), LocalTime.of(10, 0),
                room, taught));

        mvc.perform(post("/academic/timetables").header("Authorization", admin())
                        .contentType("application/json")
                        .content(booking("Tuesday", "09:00", "10:00", room, taught)))
                .andExpect(status().isCreated());
    }

    /**
     * Editing a booking without moving it must not find the booking itself in the
     * way. The check skips the row being updated by id, which is the only reason
     * changing a subject on an existing class is possible at all.
     */
    @Test
    @DisplayName("a booking being edited does not clash with itself")
    void aBookingDoesNotClashWithItself() throws Exception {
        Classroom room = classroom("A-101");
        Subject taught = aSubject("CS101");
        Timetable existing = timetables.save(new Timetable(null, "Monday",
                LocalTime.of(9, 0), LocalTime.of(10, 0), room, taught));

        mvc.perform(put("/academic/timetables/" + existing.getId()).header("Authorization", admin())
                        .contentType("application/json")
                        .content(booking("Monday", "09:00", "10:00", room, taught)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a class that ends before it starts is refused")
    void reversedTimesAreRefused() throws Exception {
        mvc.perform(post("/academic/timetables").header("Authorization", admin())
                        .contentType("application/json")
                        .content(booking("Monday", "11:00", "10:00",
                                classroom("A-101"), aSubject("CS101"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("end after it starts")));
    }

    @Test
    @DisplayName("a day that is not a weekday name is refused by validation")
    void anInventedDayIsRefused() throws Exception {
        mvc.perform(post("/academic/timetables").header("Authorization", admin())
                        .contentType("application/json")
                        .content(booking("Someday", "09:00", "10:00",
                                classroom("A-101"), aSubject("CS101"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("day of the week")));
    }
}




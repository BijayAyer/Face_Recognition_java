package com.fras.dao.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Shared JSON support for the Academic Setup DAOs (Department, Semester,
 * Subject, Classroom, Timetable), which talk to the backend's
 * {@code /academic/*} endpoints through {@link com.fras.service.ApiService}.
 * Package-private: only the DAO implementations beside it need it.
 *
 * <p><b>Nothing here returns a fallback value any more.</b> Every method used to
 * answer a failure with something that looked like success - an unreadable list
 * became an empty list, an unreadable object became null, and an object that
 * would not serialize became the string {@code "{}"}, which was then POSTed to
 * the server as though it were a record. All three hid the failure from the one
 * person who could do something about it: the empty list is why "the server is
 * unreachable" and "there are no departments yet" looked identical on screen,
 * and it is why {@code TimetableDAOImpl}'s classroom-conflict check used to
 * clear a double booking whenever the check itself had failed. A failure is now
 * thrown, carrying a sentence fit to put in front of a person.
 */
final class AcademicJson {

    /**
     * The one mapper the Academic Setup DAOs share.
     *
     * <p>{@code WRITE_DATES_AS_TIMESTAMPS} is turned off deliberately. A bare
     * {@code ObjectMapper} has it on, which writes a {@code LocalTime} as the
     * array {@code [9,0]}, while Spring Boot configures the server's mapper the
     * other way and answers with the string {@code "09:00:00"}. Both sides can
     * read both forms, so the timetable did round-trip - but the two directions
     * disagreed about what a time is, and every hand-built time elsewhere in the
     * client (the {@code date=} and {@code sessionStartTime=} query parameters in
     * {@code ApiService}) is ISO text. One format, matching the server's.
     */
    static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private AcademicJson() {
    }

    /**
     * A JSON array as a typed list.
     *
     * <p>A blank body is the one thing still treated as "nothing", because that
     * is what an endpoint with no rows may legitimately answer with.
     *
     * @throws RuntimeException if the body is not a readable array of {@code type}
     */
    static <T> List<T> parseList(String json, Class<T> type) {

        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        try {
            JavaType listType = MAPPER.getTypeFactory().constructCollectionType(List.class, type);
            return MAPPER.readValue(json, listType);
        } catch (Exception unreadable) {
            throw new RuntimeException("The server's reply was not a list of "
                    + plural(type) + ".", unreadable);
        }
    }

    /**
     * One record as JSON.
     *
     * @throws RuntimeException if the record cannot be serialized, rather than
     *                          sending an empty object in its place
     */
    static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception unserializable) {
            throw new RuntimeException("This "
                    + value.getClass().getSimpleName().toLowerCase(Locale.ROOT)
                    + " could not be prepared for the server.", unserializable);
        }
    }

    /**
     * Turns a failed call into something worth showing a person.
     *
     * <p>The wording matters more than it looks. This message is what the
     * Academic Setup screens put in front of the user when a write is refused,
     * and the commonest refusal by far is a duplicate code - which the server
     * already explains perfectly well, so its own words are kept when it sent
     * any.
     *
     * @param action what was being attempted, phrased to follow "Could not",
     *               for example {@code "add the department"}
     */
    static RuntimeException failed(String action, Exception cause) {
        String detail = cause.getMessage();
        return new RuntimeException(detail == null || detail.isBlank()
                ? "Could not " + action + "."
                : "Could not " + action + ": " + detail, cause);
    }

    /** "department" -> "departments", "timetable" -> "timetable entries". */
    private static String plural(Class<?> type) {
        String name = type.getSimpleName().toLowerCase(Locale.ROOT);
        return name.equals("timetable") ? "timetable entries" : name + "s";
    }
}

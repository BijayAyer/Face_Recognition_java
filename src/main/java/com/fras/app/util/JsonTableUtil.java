package com.fras.app.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Turns a JSON array response body into a typed list a {@code TableView} can
 * show directly.
 *
 * <p>This used to catch every failure and return an empty list, which meant a
 * payload the client could not understand was presented to the person as "no
 * records found" - indistinguishable from a genuinely empty table and
 * impossible to debug from the screen. A body that cannot be read is now
 * reported, and only a blank body counts as nothing.
 */
public final class JsonTableUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonTableUtil() {
    }

    /**
     * @param json a JSON array; blank or null means an empty list
     * @throws IOException when the body is not a readable array of {@code rowType}
     */
    public static <T> List<T> parseList(String json, Class<T> rowType) throws IOException {

        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        JavaType listType = MAPPER.getTypeFactory()
                .constructCollectionType(List.class, rowType);
        try {
            return MAPPER.readValue(json, listType);
        } catch (IOException unreadable) {
            throw new IOException(
                    "The server's reply was not a list of "
                            + rowType.getSimpleName().replace("Row", "").toLowerCase(java.util.Locale.ROOT)
                            + " records.",
                    unreadable);
        }
    }
}

package com.school.school_management_system.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One place that decides what an API error looks like on the wire, so the
 * security filters and {@link GlobalExceptionHandler} cannot drift apart.
 *
 * <pre>
 * { "timestamp": "...", "status": 401, "error": "Unauthorized",
 *   "message": "..." }
 * </pre>
 */
public final class ApiErrors {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ApiErrors() {
    }

    public static Map<String, Object> body(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }

    /** Writes the error straight to the response; used from servlet filters. */
    public static void write(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        MAPPER.writeValue(response.getOutputStream(), body(status, message));
    }
}

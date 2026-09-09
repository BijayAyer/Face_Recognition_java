package com.school.school_management_system.exception;

import com.attendance.exception.AttendanceAccessDeniedException;
import com.attendance.exception.DuplicateAttendanceException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns exceptions into the one JSON error shape defined by
 * {@link ApiErrors}, so the desktop client can show the server's own words
 * instead of "Request failed: 500".
 *
 * <p>Two rules are applied throughout:
 * <ul>
 *   <li><b>Never leak internals.</b> Anything unexpected is logged with its
 *       stack trace and answered with a fixed sentence; SQL text, class
 *       names and file paths do not reach the client.</li>
 *   <li><b>Use a status the client can branch on.</b> 401 means
 *       re-authenticate, 403 means this account may not do it, 409 means a
 *       duplicate, 429 means wait. Most of these previously arrived as a
 *       500 or as an indistinguishable 403.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String GENERIC_SERVER_MESSAGE =
            "Something went wrong on the server. Please try again.";

    // ------------------------------------------------------------------
    // 400 - the request itself is wrong
    // ------------------------------------------------------------------

    /** Bean Validation on a {@code @Valid @RequestBody}; reports every field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = ex.getBindingResult().getGlobalErrors().stream()
                    .map(oe -> oe.getDefaultMessage() == null ? "is invalid" : oe.getDefaultMessage())
                    .distinct()
                    .collect(Collectors.joining("; "));
        }
        return respond(HttpStatus.BAD_REQUEST, blankTo(message, "The submitted values are not valid."));
    }

    /** Validation that fires on a path/query parameter, or at JPA flush time. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations() == null ? "" : ex.getConstraintViolations().stream()
                .map(v -> lastNode(v.getPropertyPath().toString()) + " " + v.getMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return respond(HttpStatus.BAD_REQUEST, blankTo(message, "The submitted values are not valid."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body", ex);
        return respond(HttpStatus.BAD_REQUEST, "The request body is missing or is not valid JSON.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(HttpStatus.BAD_REQUEST, "'" + ex.getName() + "' is not in the expected format.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return respond(HttpStatus.BAD_REQUEST,
                "Required parameter '" + ex.getParameterName() + "' is missing.");
    }

    /**
     * A malformed date or time. The attendance endpoints parse {@code date}
     * and {@code sessionStartTime} straight out of the query string, and a
     * typo there used to surface as a 500.
     */
    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<Map<String, Object>> handleDateTimeParse(DateTimeParseException ex) {
        return respond(HttpStatus.BAD_REQUEST, "'" + ex.getParsedString()
                + "' is not a valid date or time. Use yyyy-MM-dd for dates and HH:mm for times.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return respond(HttpStatus.BAD_REQUEST, messageOr(ex, "The request could not be processed."));
    }

    // ------------------------------------------------------------------
    // 401 / 403 / 429 - who you are and what you may do
    // ------------------------------------------------------------------

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        // AuthService already phrases this so that it cannot be used to work
        // out whether an email is registered, so pass it through as-is.
        return respond(HttpStatus.UNAUTHORIZED, messageOr(ex, "Incorrect email or password."));
    }

    /** Disabled, locked, expired: the password was right, the account is not usable. */
    @ExceptionHandler(AccountStatusException.class)
    public ResponseEntity<Map<String, Object>> handleAccountStatus(AccountStatusException ex) {
        return respond(HttpStatus.FORBIDDEN, messageOr(ex, "This account cannot sign in."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        log.debug("Authentication failed", ex);
        return respond(HttpStatus.UNAUTHORIZED, "Please sign in again.");
    }

    /**
     * A refusal from {@code AttendanceAccessPolicy}. These messages are
     * written for the person reading them - "You can only view your own
     * attendance" - so unlike the generic handler below, this one passes the
     * message through instead of replacing it.
     */
    @ExceptionHandler(AttendanceAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAttendanceAccessDenied(
            AttendanceAccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN,
                messageOr(ex, "Your account does not have permission to do that."));
    }

    /**
     * Everything else that Spring Security refuses. The message is replaced
     * rather than forwarded: it comes from the filter chain or from method
     * security, where it is either the literal "Access Denied" or a
     * description of the rule that matched - neither of which should be
     * shown to a caller.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN, "Your account does not have permission to do that.");
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyAttempts(TooManyAttemptsException ex) {
        return respond(HttpStatus.TOO_MANY_REQUESTS, messageOr(ex, "Too many attempts. Try again later."));
    }

    // ------------------------------------------------------------------
    // 404 / 405 / 409 / 415
    // ------------------------------------------------------------------

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "That endpoint does not exist.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "That HTTP method is not supported on this endpoint.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Send this request as application/json.");
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleEmailExists(EmailAlreadyExistsException ex) {
        return respond(HttpStatus.CONFLICT, messageOr(ex, "That email address is already in use."));
    }

    @ExceptionHandler(DuplicateAttendanceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateAttendance(DuplicateAttendanceException ex) {
        return respond(HttpStatus.CONFLICT, messageOr(ex, "That attendance record already exists."));
    }

    /**
     * A delete was refused because something still depends on the record. The
     * thrower's own message is used, because it is the only thing that knows
     * what was in the way; the fallback is there so a message-less instance
     * cannot produce an empty 409.
     */
    @ExceptionHandler(RecordInUseException.class)
    public ResponseEntity<Map<String, Object>> handleRecordInUse(RecordInUseException ex) {
        return respond(HttpStatus.CONFLICT,
                messageOr(ex, "That record is still in use, so it cannot be deleted."));
    }

    /**
     * A unique key or foreign key rejected the write. The driver's message
     * carries table and index names, so it is logged and translated rather
     * than forwarded to the client.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("The database rejected a write: {}", ex.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, describeIntegrityViolation(ex));
    }

    private static String describeIntegrityViolation(DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause().getMessage();
        String lower = detail == null ? "" : detail.toLowerCase(Locale.ROOT);
        if (lower.contains("uk_attendance_student_subject_date") || lower.contains("attendance_date")) {
            return "Attendance for that student, subject and date has already been recorded.";
        }
        if (lower.contains("email")) {
            return "That email address is already in use.";
        }
        if (lower.contains("foreign key") || lower.contains("referential integrity")) {
            return "That record is still referenced by other data, "
                    + "or it points at something that no longer exists.";
        }
        if (lower.contains("null not allowed") || lower.contains("cannot be null")) {
            return "A required field was left empty.";
        }
        return "That change conflicts with data already in the database.";
    }

    // ------------------------------------------------------------------
    // Explicit statuses, then the catch-all
    // ------------------------------------------------------------------

    /** Something upstream already chose its own status; respect it. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return respond(status, blankTo(ex.getReason(), status.getReasonPhrase()));
    }

    /**
     * Last resort. The stack trace goes to the log, never to the client -
     * {@code server.error.include-stacktrace=never} covers the container's
     * own error page, and this covers everything that reaches a controller.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAnythingElse(Exception ex) {
        log.error("Unhandled exception while serving a request", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_SERVER_MESSAGE);
    }

    // ------------------------------------------------------------------

    private static ResponseEntity<Map<String, Object>> respond(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiErrors.body(status, message));
    }

    private static String messageOr(Exception ex, String fallback) {
        return blankTo(ex.getMessage(), fallback);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** {@code createStudent.student.email} becomes {@code email}. */
    private static String lastNode(String propertyPath) {
        if (propertyPath == null || propertyPath.isBlank()) {
            return "value";
        }
        int dot = propertyPath.lastIndexOf('.');
        return dot < 0 ? propertyPath : propertyPath.substring(dot + 1);
    }
}

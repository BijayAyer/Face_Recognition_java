package com.attendance.exception;

import org.springframework.security.access.AccessDeniedException;

/**
 * A permission refusal whose wording is safe to show the person.
 *
 * <p>{@code GlobalExceptionHandler} answers a plain
 * {@link AccessDeniedException} with one fixed sentence, on purpose: those
 * come out of the filter chain and out of framework internals, where the
 * message is either the literal string "Access Denied" or something that
 * describes the configuration rather than the caller. The refusals raised by
 * {@code AttendanceAccessPolicy} are different - they are written for the
 * person reading them and say which record they may actually see - so they
 * get their own type and their own handler.
 *
 * <p>It still extends {@code AccessDeniedException}, so if one of these ever
 * escapes past the controller advice, Spring Security's own translation
 * filter turns it into a 403 rather than a 500.
 */
public class AttendanceAccessDeniedException extends AccessDeniedException {

    private static final long serialVersionUID = 1L;

    public AttendanceAccessDeniedException(String message) {
        super(message);
    }
}

package com.school.school_management_system.exception;

/**
 * Raised when an account has used up its sign-in attempt budget.
 * Maps to HTTP 429 so a client can tell it apart from wrong credentials.
 */
public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException(String message) {
        super(message);
    }
}

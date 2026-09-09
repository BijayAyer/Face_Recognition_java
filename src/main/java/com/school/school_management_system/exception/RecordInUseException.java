package com.school.school_management_system.exception;

/**
 * Raised when a record cannot be deleted because other data still depends on
 * it. Maps to HTTP 409.
 *
 * <p>The message is written for the person who pressed Delete, so it names the
 * obstacle and says what to do about it. It is built by the caller rather than
 * assembled here, because only the caller knows what was in the way.
 *
 * <p>This exists so the rule can live in the service, where it shares a
 * transaction with the delete itself, without dragging a web type into the
 * service layer. The academic controllers in {@code com.fras.web} throw
 * {@code ResponseStatusException} for the same purpose - they are web classes,
 * so there is nothing to keep out.
 */
public class RecordInUseException extends RuntimeException {

    public RecordInUseException(String message) {
        super(message);
    }
}

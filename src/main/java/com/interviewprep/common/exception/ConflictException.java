package com.interviewprep.common.exception;

/**
 * Thrown by the service layer when a request conflicts with existing state, e.g. a duplicate. Mapped to HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

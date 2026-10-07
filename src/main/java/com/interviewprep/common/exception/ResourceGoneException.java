package com.interviewprep.common.exception;

/**
 * Thrown by the service layer when a resource existed but is permanently no longer available. Mapped to HTTP 410.
 */
public class ResourceGoneException extends RuntimeException {

    public ResourceGoneException(String message) {
        super(message);
    }
}

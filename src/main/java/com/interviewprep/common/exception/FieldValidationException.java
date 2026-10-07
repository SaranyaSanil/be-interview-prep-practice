package com.interviewprep.common.exception;

/**
 * Thrown by the service layer when a field breaks a rule that Bean Validation cannot express because it depends on
 * existing state. Mapped to HTTP 400 with the same field-level error shape as Bean Validation failures.
 */
public class FieldValidationException extends RuntimeException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}

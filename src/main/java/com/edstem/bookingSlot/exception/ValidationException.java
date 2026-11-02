package com.edstem.bookingSlot.exception;

import lombok.Getter;

import java.util.Collections;
import java.util.Map;

@Getter
public class ValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public ValidationException(String message) {
        super(message);
        this.fieldErrors = Collections.emptyMap();
    }

    public ValidationException(String field, String message) {
        super("Validation failed");
        this.fieldErrors = Collections.singletonMap(field, message);
    }

    public ValidationException(Map<String, String> fieldErrors) {
        super("Validation failed");
        this.fieldErrors = fieldErrors != null ? fieldErrors : Collections.emptyMap();
    }
}

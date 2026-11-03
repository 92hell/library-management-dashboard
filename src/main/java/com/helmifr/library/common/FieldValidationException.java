package com.helmifr.library.common;

import java.util.Map;

/** A business-rule validation failure tied to one request field. */
public class FieldValidationException extends RuntimeException {
    private final Map<String, String> fieldErrors;

    public FieldValidationException(String field, String message) {
        super(message);
        this.fieldErrors = Map.of(field, message);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}

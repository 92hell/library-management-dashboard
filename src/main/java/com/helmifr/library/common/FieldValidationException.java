package com.helmifr.library.common;

import java.util.Map;

/** A business-rule validation failure tied to one request field; the message is translated (see Messages). */
public class FieldValidationException extends RuntimeException implements LocalizedMessage {
    private final String field;
    private final String template;
    private final transient Object[] args;

    public FieldValidationException(String field, String template, Object... args) {
        super(Messages.english(template, args));
        this.field = field;
        this.template = template;
        this.args = args;
    }

    /** In English (logs, tests); the API response uses the reader's language. */
    public Map<String, String> getFieldErrors() {
        return Map.of(field, getMessage());
    }

    public String field() {
        return field;
    }

    @Override
    public String template() {
        return template;
    }

    @Override
    public Object[] args() {
        return args;
    }
}

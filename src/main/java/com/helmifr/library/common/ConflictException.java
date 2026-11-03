package com.helmifr.library.common;

/**
 * The request is valid but clashes with the current state (e.g. lending a book that is already out).
 * The message is an English template with {0}-style values, translated for the reader (see Messages).
 */
public class ConflictException extends RuntimeException implements LocalizedMessage {
    private final String template;
    private final transient Object[] args;

    public ConflictException(String template, Object... args) {
        super(Messages.english(template, args));
        this.template = template;
        this.args = args;
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

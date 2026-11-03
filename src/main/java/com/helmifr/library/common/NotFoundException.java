package com.helmifr.library.common;

/** Something asked for does not exist (404). Templates are translated for the reader (see Messages). */
public class NotFoundException extends RuntimeException implements LocalizedMessage {
    private final String template;
    private final transient Object[] args;

    /** "Book 42 not found" (template "Book {0} not found"). */
    public NotFoundException(String entity, Long id) {
        this(entity + " {0} not found", new Object[] {id});
    }

    /** A template with values, e.g. {@code new NotFoundException("No copy has barcode {0}", barcode)}. */
    public NotFoundException(String template, Object... args) {
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

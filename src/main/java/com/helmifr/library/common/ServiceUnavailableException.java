package com.helmifr.library.common;

/** An outside service the request depends on could not be reached (503); the message is translated. */
public class ServiceUnavailableException extends RuntimeException implements LocalizedMessage {
    private final String template;

    public ServiceUnavailableException(String template, Throwable cause) {
        super(template, cause);
        this.template = template;
    }

    @Override
    public String template() {
        return template;
    }

    @Override
    public Object[] args() {
        return new Object[0];
    }
}

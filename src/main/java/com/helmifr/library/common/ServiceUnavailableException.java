package com.helmifr.library.common;

/** An outside service the request depends on could not be reached (503). */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

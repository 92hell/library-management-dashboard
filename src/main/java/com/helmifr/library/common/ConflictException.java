package com.helmifr.library.common;

/** The request is valid but clashes with the current state (e.g. lending a book that is already out). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}

package com.helmifr.library.common;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String entity, Long id) {
        super(entity + " " + id + " not found");
    }

    public NotFoundException(String message) {
        super(message);
    }
}

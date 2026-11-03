package com.helmifr.library.common;

/** An exception whose message is a template plus values, shown in the reader's language. */
public interface LocalizedMessage {
    String template();

    Object[] args();
}

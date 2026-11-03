package com.helmifr.library.author;

import java.time.LocalDate;

public record AuthorResponse(Long id, String name, LocalDate dateOfBirth) {
    static AuthorResponse from(Author a) {
        return new AuthorResponse(a.getId(), a.getName(), a.getDateOfBirth());
    }
}

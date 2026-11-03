package com.helmifr.library.author;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AuthorRequest(
        @NotBlank @Size(max = 255) String name,
        @Past LocalDate dateOfBirth) {}

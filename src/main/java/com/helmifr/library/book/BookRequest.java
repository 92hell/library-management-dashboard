package com.helmifr.library.book;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record BookRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull @PastOrPresent LocalDate publishedDate,
        @Size(max = 255) String publisherName,
        @NotNull Long authorId,
        @Size(max = 10) List<@NotBlank String> categories,
        /** Copies to create with a new book (default 1); ignored on update - manage copies instead. */
        @Min(0) @Max(50) Integer copies) {}

package com.helmifr.library.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record MemberRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @Past LocalDate dateOfBirth,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9][0-9 -]{4,13}$",
                message = "must be 5-15 characters of digits, spaces or dashes, optionally starting with +")
        String phone,
        Boolean active) {}

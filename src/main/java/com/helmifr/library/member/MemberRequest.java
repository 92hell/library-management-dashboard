package com.helmifr.library.member;

import com.helmifr.library.common.PhoneNumbers;
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
        @Pattern(regexp = PhoneNumbers.PATTERN, message = "{library.validation.phone}")
        String phone,
        Boolean active) {}

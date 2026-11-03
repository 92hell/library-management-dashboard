package com.helmifr.library.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Phone number matching")
class PhoneNumbersTest {

    @ParameterizedTest(name = "{0} == {1}")
    @CsvSource({
            "081234567890, 081234567890",
            "081234567890, +62 812-3456-7890",
            "0812 3456 7890, 62812345678 90",
    })
    void sameNumberInDifferentFormats(String a, String b) {
        assertThat(PhoneNumbers.same(a, b)).isTrue();
    }

    @ParameterizedTest(name = "{0} != {1}")
    @CsvSource({
            "081234567890, 081234567891",
            "081234567890, 4567890",
            "'', ''",
    })
    void differentOrTooShort(String a, String b) {
        assertThat(PhoneNumbers.same(a, b)).isFalse();
    }

    @Test
    @DisplayName("masking keeps only the last 4 digits (for logs)")
    void mask() {
        assertThat(PhoneNumbers.mask("+62 812-3456-7890")).isEqualTo("*********7890");
    }

    @Test
    @DisplayName("normalising keeps digits and a leading + only")
    void normalize() {
        assertThat(PhoneNumbers.normalize(" +62 (812) 3456-7890 ")).isEqualTo("+6281234567890");
        assertThat(PhoneNumbers.normalize("0812 3456 7890")).isEqualTo("081234567890");
    }
}

package com.helmifr.library.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchPatternsTest {

    @Test
    void blankMeansNoFilter() {
        assertThat(SearchPatterns.contains(null)).isNull();
        assertThat(SearchPatterns.contains("   ")).isNull();
    }

    @Test
    void lowercasesTrimsAndEscapesWildcards() {
        assertThat(SearchPatterns.contains("  Harry ")).isEqualTo("%harry%");
        assertThat(SearchPatterns.contains("50%_off!")).isEqualTo("%50!%!_off!!%");
    }
}

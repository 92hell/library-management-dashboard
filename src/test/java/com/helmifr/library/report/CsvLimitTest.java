package com.helmifr.library.report;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** A CSV export is capped (here at 2 rows) so one download cannot exhaust the server's memory. */
@IntegrationTest
@TestPropertySource(properties = "library.reports.max-csv-rows=2")
@DisplayName("CSV export size limit")
class CsvLimitTest {

    @Autowired Api api;
    @Autowired TestData data;

    @Test
    @DisplayName("more loans than the limit in the range asks for a shorter range instead of a huge file")
    void capped() throws Exception {
        var librarian = data.librarian("Lina");
        var member = data.member("Siti");
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 3; i++) {
            data.activeLoan(data.book("Book " + i), member, today.minusDays(i), today.plusDays(14));
        }

        api.as(librarian).get("/api/reports/loans.csv")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.from").value(org.hamcrest.Matchers.containsString("shorter range")));
    }
}

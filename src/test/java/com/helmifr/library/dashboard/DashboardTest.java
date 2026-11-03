package com.helmifr.library.dashboard;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.helmifr.library.member.Member;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@DisplayName("Librarian dashboard")
class DashboardTest {

    @Autowired Api api;
    @Autowired TestData data;

    @Test
    @DisplayName("summarises pending requests, active, overdue and due-today loans")
    void summary() throws Exception {
        LocalDate today = LocalDate.now();
        Member member = data.member("Ani");
        data.pendingRequest(data.book("Requested"), member);
        data.activeLoan(data.book("Late"), member, today.minusDays(20), today.minusDays(3));
        data.activeLoan(data.book("Due today"), member, today.minusDays(14), today);
        data.activeLoan(data.book("Fine"), member, today, today.plusDays(14));

        api.as(data.librarian("Lina")).get("/api/dashboard")
                .andExpect(jsonPath("$.pendingRequests").value(1))
                .andExpect(jsonPath("$.activeLoans").value(3))
                .andExpect(jsonPath("$.overdueLoans").value(1))
                .andExpect(jsonPath("$.dueToday").value(1))
                .andExpect(jsonPath("$.books").value(4))
                .andExpect(jsonPath("$.members").value(1));
    }
}

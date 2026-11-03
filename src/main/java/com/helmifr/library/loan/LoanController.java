package com.helmifr.library.loan;

import com.helmifr.library.common.PageResponse;
import com.helmifr.library.loan.LoanDecisions.Approval;
import com.helmifr.library.loan.LoanDecisions.Rejection;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Librarian loan management. Exposed as "borrowed-books" to keep the URLs the UI has always used. */
@RestController
@RequestMapping("/api/borrowed-books")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    @GetMapping
    public PageResponse<LoanResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate borrowDate,
            @RequestParam(defaultValue = "all") String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return loanService.search(q, borrowDate, LoanFilter.parse(status), pageable);
    }

    @GetMapping("/{id}")
    public LoanResponse get(@PathVariable Long id) {
        return loanService.get(id);
    }

    @PostMapping
    public ResponseEntity<LoanResponse> create(@Valid @RequestBody LoanRequest request) {
        LoanResponse created = loanService.create(request);
        return ResponseEntity.created(URI.create("/api/borrowed-books/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public LoanResponse update(@PathVariable Long id, @Valid @RequestBody LoanRequest request) {
        return loanService.update(id, request);
    }

    @PostMapping("/{id}/approve")
    public LoanResponse approve(@PathVariable Long id, @Valid @RequestBody(required = false) Approval approval) {
        return loanService.approve(id, approval);
    }

    @PostMapping("/{id}/reject")
    public LoanResponse reject(@PathVariable Long id, @Valid @RequestBody(required = false) Rejection rejection) {
        return loanService.reject(id, rejection);
    }

    @PostMapping("/{id}/return")
    public LoanResponse markReturned(@PathVariable Long id) {
        return loanService.markReturned(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        loanService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

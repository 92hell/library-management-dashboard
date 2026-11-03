package com.helmifr.library.loan;

import com.helmifr.library.loan.LoanDecisions.BorrowRequest;
import com.helmifr.library.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** The signed-in customer's own loans and borrow requests. */
@RestController
@RequestMapping("/api/my/loans")
@RequiredArgsConstructor
public class MyLoanController {

    private final MyLoanService myLoans;

    @GetMapping
    public List<LoanResponse> list() {
        return myLoans.list(CurrentUser.memberId());
    }

    @PostMapping
    public ResponseEntity<LoanResponse> request(@Valid @RequestBody BorrowRequest request) {
        LoanResponse created = myLoans.request(CurrentUser.memberId(), request.bookId());
        return ResponseEntity.created(URI.create("/api/my/loans/" + created.id())).body(created);
    }

    @PostMapping("/{id}/cancel")
    public LoanResponse cancel(@PathVariable Long id) {
        return myLoans.cancel(CurrentUser.memberId(), id);
    }

    @PostMapping("/{id}/renew")
    public LoanResponse renew(@PathVariable Long id) {
        return myLoans.renew(CurrentUser.memberId(), id);
    }
}

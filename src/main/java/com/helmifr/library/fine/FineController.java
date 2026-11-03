package com.helmifr.library.fine;

import com.helmifr.library.common.PageResponse;
import com.helmifr.library.fine.FineDtos.FineResponse;
import com.helmifr.library.fine.FineDtos.MyFines;
import com.helmifr.library.fine.FineDtos.Waiver;
import com.helmifr.library.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

/** Librarians: list, pay and waive fines (/api/fines). Customers: their own (/api/my/fines). */
@RestController
@RequiredArgsConstructor
public class FineController {

    private final FineService fineService;

    @GetMapping("/api/fines")
    public PageResponse<FineResponse> list(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) FineStatus status,
                                           @PageableDefault(size = 20) Pageable pageable) {
        return fineService.search(q, status, pageable);
    }

    @PostMapping("/api/fines/{id}/pay")
    public FineResponse pay(@PathVariable Long id) {
        return fineService.pay(id);
    }

    @PostMapping("/api/fines/{id}/waive")
    public FineResponse waive(@PathVariable Long id, @Valid @RequestBody Waiver waiver) {
        return fineService.waive(id, waiver.reason());
    }

    @GetMapping("/api/my/fines")
    public MyFines mine() {
        return fineService.mine(CurrentUser.memberId());
    }
}

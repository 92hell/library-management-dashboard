package com.helmifr.library.receipt;

import com.helmifr.library.receipt.ReceiptService.ReceiptView;
import com.helmifr.library.receipt.ReceiptService.Verification;
import com.helmifr.library.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receipts: librarians issue them for any loan, customers for their own; anyone can verify one
 * with the code from its QR link, or check signatures with the public key.
 */
@RestController
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receipts;

    @PostMapping("/api/borrowed-books/{loanId}/receipt")
    public ReceiptView issueForLibrarian(@PathVariable Long loanId) {
        return receipts.issue(loanId, null);
    }

    @PostMapping("/api/my/loans/{loanId}/receipt")
    public ReceiptView issueForMember(@PathVariable Long loanId) {
        return receipts.issue(loanId, CurrentUser.memberId());
    }

    @GetMapping("/api/receipts/{number:[A-Z0-9-]+}")
    public ReceiptView get(@PathVariable String number) {
        return receipts.get(number);
    }

    @GetMapping("/api/receipts/{number:[A-Z0-9-]+}.pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String number) {
        Receipt r = receipts.requireVisible(number);
        byte[] pdf = ReceiptPdf.render(receipts.view(r), receipts.parse(r.getContent()));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("receipt-" + number + ".pdf").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf);
    }

    /** Public: the record behind a receipt and whether its signature holds. */
    @GetMapping("/api/receipts/verify/{number}")
    public Verification verify(@PathVariable String number, @RequestParam(required = false) String code) {
        return receipts.verify(number, code);
    }

    /** Public: the key that signs receipts (PEM), for checking signatures independently. */
    @GetMapping(value = "/api/receipts/public-key", produces = MediaType.TEXT_PLAIN_VALUE)
    public String publicKey() {
        return receipts.publicKeyPem();
    }
}

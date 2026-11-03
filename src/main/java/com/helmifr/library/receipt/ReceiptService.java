package com.helmifr.library.receipt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.helmifr.library.account.Role;
import com.helmifr.library.common.Audit;
import com.helmifr.library.common.NotFoundException;
import com.helmifr.library.fine.FinePolicy;
import com.helmifr.library.fine.FineRepository;
import com.helmifr.library.loan.Loan;
import com.helmifr.library.loan.LoanEvents;
import com.helmifr.library.loan.LoanRepository;
import com.helmifr.library.receipt.ReceiptContent.EventPart;
import com.helmifr.library.receipt.ReceiptContent.FinePart;
import com.helmifr.library.receipt.ReceiptContent.LoanPart;
import com.helmifr.library.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Issues, shows and verifies loan receipts. A receipt freezes the loan, its history and its late fee
 * into canonical JSON, which is hashed (SHA-256) and signed ({@link ReceiptSigner}). The QR code
 * links to a public page that re-checks the signature over the stored text.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceiptService {

    /** Length of the code in the verification link (start of the URL-safe signature). */
    static final int CODE_LENGTH = 12;

    /** At most this many history entries on a receipt; older ones are summarised in one line. */
    static final int MAX_EVENTS = 40;

    private static final ObjectMapper CANONICAL = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    /** Matrix of the QR code: {@code modules} is size x size characters, '1' = dark, row by row. */
    public record Qr(int size, String modules) {}

    public record ReceiptView(String number, Instant issuedAt, String issuedBy, String algorithm, String keyId,
                              String sha256, String signature, String signedContent, JsonNode content,
                              String verifyCode, String verifyUrl, Qr qr, boolean genuine) {}

    public record VerifiedEvent(String type, String occurredAt) {}

    /** What the public verification page shows: the record, with the member's name masked. */
    /**
     * @param demo         issued by the public demo: not a real loan record
     * @param supersededBy a newer receipt for the same loan, if any (this one describes an older state)
     */
    public record Verification(boolean genuine, String number, String library, String issuedAt, String bookTitle,
                               String copyBarcode, String memberName, String status, String borrowDate,
                               String dueDate, String returnDate, List<VerifiedEvent> events, FinePart fine,
                               String algorithm, String keyId, String sha256, boolean demo, String supersededBy) {}

    private final ReceiptRepository receipts;
    private final LoanRepository loans;
    private final LoanEvents history;
    private final FineRepository fines;
    private final FinePolicy finePolicy;
    private final ReceiptSigner signer;
    private final ReceiptProperties properties;
    private final Clock clock;
    private final com.helmifr.library.demo.DemoProperties demo;

    /**
     * The receipt for a loan as it is now: the latest one if nothing changed since, otherwise a new one.
     *
     * @param ownerMemberId for customers: the loan must be theirs (else 404); null for librarians
     */
    @Transactional
    public ReceiptView issue(Long loanId, Long ownerMemberId) {
        Loan loan = loans.findByIdAndDeletedFalse(loanId)
                .filter(l -> ownerMemberId == null || Objects.equals(l.getMember().getId(), ownerMemberId))
                .orElseThrow(() -> new NotFoundException("Loan", loanId));
        LoanPart loanPart = loanPart(loan);
        List<EventPart> events = summarised(history.of(loanId).stream()
                .map(e -> new EventPart(e.type().name(), e.status().name(), e.occurredAt().toString(), e.actor(), e.detail()))
                .toList());
        FinePart fine = fines.findByLoanId(loanId)
                .map(f -> new FinePart(f.getAmount(), finePolicy.currency(), f.getStatus().name(), f.getDaysLate()))
                .orElse(null);
        String fingerprint = sha256(canonical(Map.of("loan", loanPart, "events", events, "fine", fine == null ? "-" : fine)));

        var latest = receipts.findFirstByLoanIdOrderByIdDesc(loanId);
        if (latest.isPresent() && latest.get().getFingerprint().equals(fingerprint)) {
            return view(latest.get());
        }

        Receipt r = new Receipt();
        r.setLoan(loan);
        r.setMemberId(loan.getMember().getId());
        r.setIssuedAt(clock.instant().truncatedTo(ChronoUnit.SECONDS));
        r.setIssuedBy(history.currentActorName());
        r.setFingerprint(fingerprint);
        r.setContent("");
        r.setSha256("");
        r.setSignature("");
        r.setKeyId("");
        r = receipts.saveAndFlush(r);
        r.setNumber("R-%d-%06d".formatted(r.getIssuedAt().atZone(clock.getZone()).getYear(), r.getId()));

        String content = canonical(new ReceiptContent(r.getNumber(), properties.libraryName(), r.getIssuedAt().toString(),
                r.getIssuedBy(), loanPart, events, fine, demo.enabled()));
        if (content.length() > Receipt.MAX_CONTENT) {
            throw new IllegalStateException("Receipt for loan " + loanId + " is too long to store (" + content.length() + " chars)");
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        r.setContent(content);
        r.setSha256(sha256(content));
        r.setSignature(Base64.getEncoder().encodeToString(signer.sign(bytes)));
        r.setKeyId(signer.keyId());
        Audit.event("RECEIPT_ISSUED", "number={} loanId={} sha256={} by={}", r.getNumber(), loanId, r.getSha256(),
                r.getIssuedBy());
        return view(r);
    }

    /** A receipt for its owner (the member of the loan) or a librarian; anyone else gets a 404. */
    public ReceiptView get(String number) {
        return view(requireVisible(number));
    }

    Receipt requireVisible(String number) {
        Receipt r = receipts.findByNumber(number).orElseThrow(() -> notFound(number));
        if (!CurrentUser.is(Role.LIBRARIAN) && !Objects.equals(r.getMemberId(), memberIdOrNull())) {
            throw notFound(number);
        }
        return r;
    }

    /** Public: checks the signature over the stored record; the code (from the QR link) must match. */
    public Verification verify(String number, String code) {
        Receipt r = receipts.findByNumber(number)
                .filter(x -> code != null && MessageDigest.isEqual(verifyCode(x).getBytes(StandardCharsets.UTF_8),
                        code.getBytes(StandardCharsets.UTF_8)))
                .orElseThrow(() -> new NotFoundException("No receipt has this number and code."));
        ReceiptContent c = parse(r.getContent());
        return new Verification(genuine(r), r.getNumber(), c.library(), c.issuedAt(), c.loan().bookTitle(),
                c.loan().copyBarcode(), mask(c.loan().memberName()), c.loan().status(), c.loan().borrowDate(),
                c.loan().dueDate(), c.loan().returnDate(),
                c.events().stream().map(e -> new VerifiedEvent(e.type(), e.occurredAt())).toList(),
                c.fine(), signer.algorithm(), r.getKeyId(), r.getSha256(), c.demo(),
                receipts.findFirstByLoanIdOrderByIdDesc(r.getLoan().getId())
                        .filter(latest -> !latest.getId().equals(r.getId()))
                        .map(Receipt::getNumber).orElse(null));
    }

    /** The most recent entries, with the older ones (if too many) summarised in a first line. */
    static List<EventPart> summarised(List<EventPart> events) {
        if (events.size() <= MAX_EVENTS) {
            return events;
        }
        List<EventPart> kept = events.subList(events.size() - MAX_EVENTS, events.size());
        EventPart first = events.get(0);
        EventPart summary = new EventPart("OMITTED", first.status(), first.occurredAt(), "system",
                "%d earlier entries; the library keeps the full history".formatted(events.size() - MAX_EVENTS));
        List<EventPart> result = new java.util.ArrayList<>(MAX_EVENTS + 1);
        result.add(summary);
        result.addAll(kept);
        return result;
    }

    public String publicKeyPem() {
        return signer.publicKeyPem();
    }

    ReceiptView view(Receipt r) {
        String url = verifyUrl(r);
        try {
            return new ReceiptView(r.getNumber(), r.getIssuedAt(), r.getIssuedBy(), signer.algorithm(), r.getKeyId(),
                    r.getSha256(), r.getSignature(), r.getContent(), CANONICAL.readTree(r.getContent()), verifyCode(r),
                    url, qr(url), genuine(r));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored receipt " + r.getNumber() + " is not valid JSON", e);
        }
    }

    ReceiptContent parse(String content) {
        try {
            return CANONICAL.readValue(content, ReceiptContent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored receipt is not valid JSON", e);
        }
    }

    /** The stored text hashes to the stored hash, is signed by a trusted key, and belongs to this row. */
    boolean genuine(Receipt r) {
        try {
            ReceiptContent c = parse(r.getContent());
            if (!Objects.equals(c.number(), r.getNumber()) || c.loan() == null
                    || !Objects.equals(c.loan().id(), r.getLoan().getId())) {
                return false;
            }
            return sha256(r.getContent()).equals(r.getSha256())
                    && signer.verify(r.getContent().getBytes(StandardCharsets.UTF_8),
                            Base64.getDecoder().decode(r.getSignature()), r.getKeyId());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return false;
        }
    }

    String verifyUrl(Receipt r) {
        String base = properties.publicUrl() != null && !properties.publicUrl().isBlank()
                ? properties.publicUrl().replaceAll("/+$", "")
                : ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        return base + "/verify/" + r.getNumber() + "?code=" + verifyCode(r);
    }

    static String verifyCode(Receipt r) {
        byte[] sig = Base64.getDecoder().decode(r.getSignature());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sig).substring(0, CODE_LENGTH);
    }

    static Qr qr(String text) {
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 0));
            StringBuilder modules = new StringBuilder(m.getWidth() * m.getHeight());
            for (int y = 0; y < m.getHeight(); y++) {
                for (int x = 0; x < m.getWidth(); x++) {
                    modules.append(m.get(x, y) ? '1' : '0');
                }
            }
            return new Qr(m.getWidth(), modules.toString());
        } catch (WriterException e) {
            throw new IllegalStateException("Could not draw the QR code", e);
        }
    }

    /** "Siti Rahma" -> "S**i R***a". */
    static String mask(String name) {
        if (name == null) {
            return null;
        }
        return Arrays.stream(name.trim().split("\\s+"))
                .map(w -> w.length() <= 2 ? w.charAt(0) + "*" : w.charAt(0) + "*".repeat(w.length() - 2) + w.charAt(w.length() - 1))
                .collect(Collectors.joining(" "));
    }

    private static LoanPart loanPart(Loan l) {
        return new LoanPart(l.getId(), l.getStatus().name(), l.getBook().getTitle(), l.getBook().getAuthor().getName(),
                l.getCopy() == null ? null : l.getCopy().getBarcode(), l.getMember().getName(),
                l.getMember().getLibraryCardId(),
                l.getRequestedAt() == null ? null : l.getRequestedAt().truncatedTo(ChronoUnit.SECONDS).toString(),
                str(l.getBorrowDate()), str(l.getDueDate()), str(l.getReturnDate()), l.getRenewalCount());
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String canonical(Object value) {
        try {
            return CANONICAL.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Long memberIdOrNull() {
        return CurrentUser.principal().map(p -> p.getMemberId()).orElse(null);
    }

    private static NotFoundException notFound(String number) {
        return new NotFoundException("No receipt " + number + ".");
    }
}

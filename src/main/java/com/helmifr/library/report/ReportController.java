package com.helmifr.library.report;

import com.helmifr.library.common.Audit;
import com.helmifr.library.report.ReportDtos.Summary;
import com.helmifr.library.security.CurrentUser;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Librarian reports over ?from=&to= (inclusive dates; default the last 30 days), as JSON or CSV downloads. */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final MediaType CSV = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);

    private final ReportService reports;

    @GetMapping("/summary")
    public Summary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.summary(reports.range(from, to));
    }

    @GetMapping("/loans.csv")
    public ResponseEntity<byte[]> loans(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportRange range = reports.range(from, to);
        return csv("loans", range, reports.loansCsv(range));
    }

    @GetMapping("/most-borrowed.csv")
    public ResponseEntity<byte[]> mostBorrowed(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportRange range = reports.range(from, to);
        return csv("most-borrowed", range, reports.mostBorrowedCsv(range));
    }

    private static ResponseEntity<byte[]> csv(String name, ReportRange range, byte[] body) {
        String filename = name + "-" + range.fileSuffix() + ".csv";
        Audit.event("REPORT_EXPORTED", "report={} from={} to={} by={}", name, range.from(), range.to(), CurrentUser.username());
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }
}

package com.helmifr.library.book;

import com.helmifr.library.book.CopyDtos.CopyLookup;
import com.helmifr.library.book.CopyDtos.CopyRequest;
import com.helmifr.library.book.CopyDtos.CopyResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Copy management and barcode lookup for librarians. */
@RestController
@RequiredArgsConstructor
public class CopyController {

    private final CopyService copyService;

    @GetMapping("/api/books/{bookId}/copies")
    public List<CopyResponse> list(@PathVariable Long bookId) {
        return copyService.copiesOf(bookId);
    }

    @PostMapping("/api/books/{bookId}/copies")
    public ResponseEntity<CopyResponse> add(@PathVariable Long bookId, @Valid @RequestBody CopyRequest request) {
        CopyResponse created = copyService.add(bookId, request);
        return ResponseEntity.created(URI.create("/api/copies/" + created.id())).body(created);
    }

    @PutMapping("/api/copies/{id}")
    public CopyResponse update(@PathVariable Long id, @Valid @RequestBody CopyRequest request) {
        return copyService.update(id, request);
    }

    @DeleteMapping("/api/copies/{id}")
    public ResponseEntity<Void> withdraw(@PathVariable Long id) {
        copyService.withdraw(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/copies/lookup")
    public CopyLookup lookup(@RequestParam String barcode) {
        return copyService.lookup(barcode);
    }
}

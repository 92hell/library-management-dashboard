package com.helmifr.library.book;

import com.fasterxml.jackson.databind.JsonNode;
import com.helmifr.library.author.Author;
import com.helmifr.library.author.AuthorRepository;
import com.helmifr.library.common.FieldValidationException;
import com.helmifr.library.common.NotFoundException;
import com.helmifr.library.common.ServiceUnavailableException;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Looks a book up by ISBN at Open Library (the "jscmd=data" Books API) to pre-fill the book form.
 * Nothing is saved: the librarian reviews the result and saves the form.
 */
@Service
@Transactional(readOnly = true)
public class IsbnLookup {

    /**
     * @param authorId set when an author with that name is already in the catalogue
     * @param publishedDate 1 January of the year Open Library gives, if any
     * @param categories Open Library subjects that match the catalogue's categories
     */
    public record Result(String isbn, String title, String authorName, Long authorId, String publisherName,
                         LocalDate publishedDate, List<String> categories, String coverUrl) {}

    private static final Pattern YEAR = Pattern.compile("\\b(1[5-9]\\d\\d|20\\d\\d)\\b");

    private final RestClient client;
    private final boolean enabled;
    private final AuthorRepository authors;
    private final CatalogueProperties catalogue;

    public IsbnLookup(CatalogueProperties catalogue, AuthorRepository authors, RestClient.Builder builder) {
        this.catalogue = catalogue;
        this.authors = authors;
        this.enabled = catalogue.lookupUrl() != null && !catalogue.lookupUrl().isBlank();
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(catalogue.lookupTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
        factory.setReadTimeout(catalogue.lookupTimeout());
        this.client = builder.requestFactory(factory).baseUrl(enabled ? catalogue.lookupUrl() : "http://disabled").build();
    }

    public Result lookup(String input) {
        String isbn = Isbn.toIsbn13(input).orElseThrow(
                () -> new FieldValidationException("isbn", "is not a valid ISBN-10 or ISBN-13"));
        if (!enabled) {
            throw new ServiceUnavailableException("ISBN lookup is turned off. Please fill in the details by hand.", null);
        }
        JsonNode book = fetch(isbn).path("ISBN:" + isbn);
        if (book.isMissingNode() || !book.hasNonNull("title")) {
            throw new NotFoundException("Open Library has no book with ISBN " + isbn + ".");
        }
        String authorName = text(book.path("authors").path(0).path("name"));
        Long authorId = authorName == null ? null
                : authors.findFirstByNameIgnoreCaseAndDeletedFalse(authorName.trim()).map(Author::getId).orElse(null);
        return new Result(isbn, book.path("title").asText().trim(), authorName, authorId,
                text(book.path("publishers").path(0).path("name")), year(text(book.path("publish_date"))),
                categories(book.path("subjects")), catalogue.coverUrl(isbn));
    }

    private JsonNode fetch(String isbn) {
        try {
            return client.get()
                    .uri(uri -> uri.path("/api/books")
                            .queryParam("bibkeys", "ISBN:" + isbn)
                            .queryParam("format", "json")
                            .queryParam("jscmd", "data")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new ServiceUnavailableException("Open Library could not be reached. Please fill in the details by hand.", e);
        }
    }

    private static String text(JsonNode node) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText().trim() : null;
    }

    private static LocalDate year(String publishDate) {
        if (publishDate == null) {
            return null;
        }
        Matcher m = YEAR.matcher(publishDate);
        if (!m.find()) {
            return null;
        }
        LocalDate date = LocalDate.of(Integer.parseInt(m.group(1)), 1, 1);
        return date.isAfter(LocalDate.now()) ? null : date;
    }

    private static List<String> categories(JsonNode subjects) {
        List<String> found = new ArrayList<>();
        for (JsonNode subject : subjects) {
            String name = text(subject.path("name"));
            if (name == null) {
                continue;
            }
            BookCategories.ALL.stream()
                    .filter(c -> c.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)))
                    .findFirst()
                    .filter(c -> !found.contains(c))
                    .ifPresent(found::add);
        }
        return found;
    }
}

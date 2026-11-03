package com.helmifr.library.book;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Book covers and ISBN lookup, bound from {@code library.catalogue.*}.
 *
 * @param covers        show Open Library cover images for books with an ISBN (the browser loads them)
 * @param lookupUrl     Open Library base URL for ISBN lookup; blank disables the lookup
 * @param lookupTimeout how long to wait for Open Library before giving up
 */
@ConfigurationProperties(prefix = "library.catalogue")
public record CatalogueProperties(boolean covers, String lookupUrl, Duration lookupTimeout) {

    static final String COVERS_HOST = "https://covers.openlibrary.org";

    /** The medium-size cover for an ISBN-13; "default=false" makes a missing cover a 404 the app falls back from. */
    String coverUrl(String isbn) {
        return covers && isbn != null ? COVERS_HOST + "/b/isbn/" + isbn + "-M.jpg?default=false" : null;
    }
}

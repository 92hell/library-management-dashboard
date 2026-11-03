package com.helmifr.library.book;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Barcode rules: generated labels look like BC-7KQ2XM9P; typed or scanned ones are normalised. */
@Component
@RequiredArgsConstructor
public class CopyBarcodes {

    /** Upper-case letters, digits and dashes, 4-32 characters (what common label scanners emit). */
    public static final Pattern VALID = Pattern.compile("^[A-Z0-9-]{4,32}$");

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CopyRepository copies;

    public String next() {
        String barcode;
        do {
            StringBuilder sb = new StringBuilder("BC-");
            for (int i = 0; i < 8; i++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            barcode = sb.toString();
        } while (copies.existsByBarcode(barcode));
        return barcode;
    }

    /** Scanners and people type barcodes in either case and with stray spaces. */
    public static String normalize(String barcode) {
        return barcode == null ? null : barcode.trim().toUpperCase(Locale.ROOT);
    }
}

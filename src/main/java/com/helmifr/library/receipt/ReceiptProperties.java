package com.helmifr.library.receipt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from {@code library.receipts.*}; see application.yml. */
@ConfigurationProperties(prefix = "library.receipts")
public record ReceiptProperties(String libraryName, String publicUrl, String privateKey, String publicKey) {}

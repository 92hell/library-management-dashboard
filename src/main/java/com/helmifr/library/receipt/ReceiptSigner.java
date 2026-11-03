package com.helmifr.library.receipt;

/**
 * Signs receipts and checks signatures. The default is {@link Ed25519ReceiptSigner}: it proves a
 * receipt was issued by this library and has not been altered since. A certified electronic
 * signature (Indonesia: a Kominfo-registered PSrE such as BSrE or Privy, under UU ITE / PP 71/2019)
 * would be another implementation of this interface that sends the receipt's hash to the provider.
 */
public interface ReceiptSigner {

    /** e.g. "Ed25519". */
    String algorithm();

    /** Identifies the key a signature was made with (keys can be rotated). */
    String keyId();

    byte[] sign(byte[] data);

    /** @return false for a wrong signature or an unknown key */
    boolean verify(byte[] data, byte[] signature, String keyId);

    /** The current public key as PEM, so anyone can check signatures without trusting the app. */
    String publicKeyPem();
}

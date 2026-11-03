package com.helmifr.library.receipt;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ed25519 signatures (JDK built-in). The key comes from configuration, or is generated once and kept
 * in the database; its id is the first 16 hex digits of the SHA-256 of the public key.
 */
@Component
class Ed25519ReceiptSigner implements ReceiptSigner {

    private static final Logger log = LoggerFactory.getLogger(Ed25519ReceiptSigner.class);
    private static final String ALGORITHM = "Ed25519";

    private final ReceiptProperties properties;
    private final ReceiptKeyRepository keys;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private volatile Loaded current;

    private record Loaded(String keyId, PrivateKey privateKey, PublicKey publicKey) {}

    Ed25519ReceiptSigner(ReceiptProperties properties, ReceiptKeyRepository keys, TransactionTemplate transactions,
                         Clock clock) {
        this.properties = properties;
        this.keys = keys;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public String algorithm() {
        return ALGORITHM;
    }

    @Override
    public String keyId() {
        return key().keyId();
    }

    @Override
    public byte[] sign(byte[] data) {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(key().privateKey());
            signature.update(data);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not sign the receipt", e);
        }
    }

    @Override
    public boolean verify(byte[] data, byte[] sig, String keyId) {
        PublicKey publicKey = publicKeyFor(keyId);
        if (publicKey == null) {
            return false;
        }
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(data);
            return signature.verify(sig);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    @Override
    public String publicKeyPem() {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key().publicKey().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----\n";
    }

    private PublicKey publicKeyFor(String keyId) {
        Loaded k = key();
        if (k.keyId().equals(keyId)) {
            return k.publicKey();
        }
        return keys.findByKeyId(keyId).map(stored -> decodePublic(stored.getPublicKey())).orElse(null);
    }

    private Loaded key() {
        Loaded k = current;
        if (k == null) {
            synchronized (this) {
                if (current == null) {
                    current = load();
                }
                k = current;
            }
        }
        return k;
    }

    private Loaded load() {
        if (hasText(properties.privateKey()) && hasText(properties.publicKey())) {
            PublicKey publicKey = decodePublic(properties.publicKey());
            return new Loaded(idOf(publicKey), decodePrivate(properties.privateKey()), publicKey);
        }
        ReceiptKey stored = transactions.execute(status -> keys.findFirstByOrderByIdDesc().orElseGet(this::generate));
        return new Loaded(stored.getKeyId(), decodePrivate(stored.getPrivateKey()), decodePublic(stored.getPublicKey()));
    }

    private ReceiptKey generate() {
        try {
            KeyPair pair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
            ReceiptKey k = new ReceiptKey();
            k.setKeyId(idOf(pair.getPublic()));
            k.setPublicKey(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
            k.setPrivateKey(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
            k.setCreatedAt(clock.instant());
            log.warn("No receipt signing key configured; generated key {} and stored it in the database. "
                    + "Set RECEIPT_PRIVATE_KEY / RECEIPT_PUBLIC_KEY to keep the key outside the database.", k.getKeyId());
            return keys.save(k);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not generate a receipt signing key", e);
        }
    }

    private static String idOf(PublicKey key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getEncoded())).substring(0, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static PublicKey decodePublic(String b64) {
        try {
            return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64.trim())));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid receipt public key", e);
        }
    }

    private static PrivateKey decodePrivate(String b64) {
        try {
            return KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64.trim())));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid receipt private key", e);
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}

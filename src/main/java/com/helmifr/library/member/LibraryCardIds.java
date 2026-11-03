package com.helmifr.library.member;

import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Generates library card IDs such as LIB-7KQ2XM: random (not guessable from other members'
 * IDs) and free of look-alike characters (0/O, 1/I) so they can be read out at the desk.
 */
@Component
@RequiredArgsConstructor
public class LibraryCardIds {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberRepository members;

    public String next() {
        String id;
        do {
            StringBuilder sb = new StringBuilder("LIB-");
            for (int i = 0; i < LENGTH; i++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            id = sb.toString();
        } while (members.existsByLibraryCardId(id));
        return id;
    }
}

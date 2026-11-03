package com.helmifr.library.member;

import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Generates random, hard-to-guess library card codes such as LIB-7KQ2XM. The alphabet leaves
 * out look-alike characters (0/O, 1/I) so codes can be read out at the desk.
 */
@Component
@RequiredArgsConstructor
public class MemberCodes {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberRepository members;

    public String next() {
        String code;
        do {
            StringBuilder sb = new StringBuilder("LIB-");
            for (int i = 0; i < LENGTH; i++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            code = sb.toString();
        } while (members.existsByMemberCode(code));
        return code;
    }
}

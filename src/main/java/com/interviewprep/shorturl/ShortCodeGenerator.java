package com.interviewprep.shorturl;

import java.security.SecureRandom;
import java.util.Random;

import org.springframework.stereotype.Component;

/**
 * Generates random Base62 codes. Base62 ({@code [A-Za-z0-9]}) is URL-safe without encoding, and 7 characters give
 * 62^7 ≈ 3.5 trillion combinations while staying within the 8-character limit. Random codes, unlike codes derived
 * from database ids, cannot be enumerated.
 */
@Component
public class ShortCodeGenerator {

    static final int CODE_LENGTH = 7;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final Random random;

    public ShortCodeGenerator() {
        this(new SecureRandom());
    }

    /** For tests: a seeded {@link Random} makes the generated sequence reproducible. */
    ShortCodeGenerator(Random random) {
        this.random = random;
    }

    public String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}

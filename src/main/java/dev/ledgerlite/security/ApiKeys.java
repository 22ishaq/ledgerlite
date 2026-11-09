package dev.ledgerlite.security;

import java.security.SecureRandom;
import java.util.Base64;

import dev.ledgerlite.common.Hashing;

public final class ApiKeys {

    private static final String PREFIX = "ll_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeys() {
    }

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /*
     * Keys are 256 random bits, not something a person picked, so plain SHA-256 is
     * fine. bcrypt/argon2 only help against guessable secrets, and they would stop us
     * looking a client up by the hash of its key.
     */
    public static String hash(String rawKey) {
        return Hashing.sha256Hex(rawKey);
    }
}

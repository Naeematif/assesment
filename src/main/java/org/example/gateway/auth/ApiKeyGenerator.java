package org.example.gateway.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Creates and hashes API keys.
 *
 * <p>Keys look like {@code amk_live_<32 random bytes, base64url>}. The prefix is stored in clear for
 * support purposes, the key itself is only ever persisted as a SHA-256 digest.
 *
 * <p>Plain SHA-256 (rather than bcrypt/argon2) is the right call here: the secret is 256 bits of
 * machine-generated entropy, so there is nothing to brute force, and the digest has to be cheap
 * enough to compute on every single API call.
 */
@Component
public class ApiKeyGenerator {

    private static final String PREFIX = "amk_live_";
    private static final int SECRET_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    /** @return the plaintext key; it is shown to the operator once and never stored. */
    public String generate() {
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    public String hash(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }

    /** Non-secret display fragment, e.g. {@code amk_live_a1b2c3}. */
    public String prefixOf(String rawKey) {
        int end = Math.min(rawKey.length(), PREFIX.length() + 6);
        return rawKey.substring(0, end);
    }
}

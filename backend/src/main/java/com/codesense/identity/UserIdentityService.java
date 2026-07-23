package com.codesense.identity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Issues and verifies the opaque "X-User-Id" identity used to scope history
 * in this no-login app. Previously ANY client-supplied string was trusted
 * outright (see ExecutionService/AnalysisService's userId-scoped queries) -
 * an id captured or guessed by someone else worked exactly as well as the
 * real one, with no way for the server to tell them apart. This signs every
 * id the server itself issues (see IdentityController) with an HMAC-SHA256
 * the client never sees, and {@link UserIdentityFilter} rejects (treats as
 * anonymous - the existing "no header sent" behavior, not an error) any id
 * whose signature doesn't verify - so only ids this server actually issued
 * are ever honored.
 *
 * <p>Doesn't fully close capture/replay (a leaked signed id is just as
 * reusable as a leaked unsigned one) - that needs real sessions/login, out of
 * scope for a no-login tool. What it removes is trivial forgery: before this,
 * typing any string into the header worked just as well as a real id; now it
 * must be a token this server actually minted.
 *
 * <p>Without an explicit {@code user-identity.secret}, a random secret is
 * generated once per process start (logged) - tokens issued before a restart
 * stop verifying after one, so users appear as new/anonymous. Fine for local
 * dev; production should set a persistent secret so history survives deploys.
 */
@Slf4j
@Service
public class UserIdentityService {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secretBytes;

    public UserIdentityService(@Value("${user-identity.secret:}") String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            log.warn("USER_IDENTITY_SECRET not set - generating an ephemeral signing secret for this process. "
                    + "Every previously-issued X-User-Id will stop validating (users appear as new/anonymous) "
                    + "on the next restart. Set USER_IDENTITY_SECRET so history survives deploys.");
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.secretBytes = random;
        } else {
            this.secretBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
    }

    /** A fresh, server-signed identity: {@code <uuid>.<base64url hmac>}. */
    public String issue() {
        String id = UUID.randomUUID().toString();
        return id + "." + sign(id);
    }

    /**
     * @return {@code token}'s id portion if its signature verifies, else
     *         null - callers already treat a null userId as "anonymous", so
     *         an invalid/forged/tampered token degrades to exactly the same
     *         behavior as no header at all rather than an error.
     */
    public String validateOrNull(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        int dot = token.lastIndexOf('.');
        if (dot < 0 || dot == token.length() - 1) {
            return null;
        }
        String id = token.substring(0, dot);
        String signature = token.substring(dot + 1);
        if (!constantTimeEquals(signature, sign(id))) {
            return null;
        }
        return id;
    }

    private String sign(String id) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, ALGORITHM));
            byte[] raw = mac.doFinal(id.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    /** Manual constant-time compare so signature verification doesn't leak
     * timing information about how many leading characters matched. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}

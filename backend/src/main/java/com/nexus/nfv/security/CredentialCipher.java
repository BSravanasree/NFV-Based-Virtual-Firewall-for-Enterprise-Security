package com.nexus.nfv.security;

import com.nexus.nfv.common.exception.ApiException;
import com.nexus.nfv.config.NexusProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for infrastructure credentials stored in the database (e.g. NETCONF passwords).
 * The key comes from NEXUS_CREDENTIAL_KEY (base64, 32 bytes). Without a key, storing credentials is refused.
 */
@Component
public class CredentialCipher {

    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final String PREFIX = "v1:";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CredentialCipher(NexusProperties props) {
        String k = props.security().credentialKey();
        if (k == null || k.isBlank()) {
            this.key = null;
        } else {
            byte[] raw = Base64.getDecoder().decode(k.trim());
            if (raw.length != 32) {
                throw new IllegalStateException("NEXUS_CREDENTIAL_KEY must be 32 bytes (base64-encoded)");
            }
            this.key = new SecretKeySpec(raw, "AES");
        }
    }

    public boolean available() {
        return key != null;
    }

    public String encrypt(String plain) {
        requireKey();
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (Exception e) {
            throw new IllegalStateException("Credential encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        requireKey();
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalStateException("Unsupported credential format");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
            return new String(c.doFinal(all, IV_LEN, all.length - IV_LEN), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Credential decryption failed (wrong NEXUS_CREDENTIAL_KEY?)", e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CREDENTIAL_KEY_MISSING",
                    "NEXUS_CREDENTIAL_KEY is not configured; infrastructure credentials cannot be stored");
        }
    }

    /** Random password generator for per-VNF NETCONF credentials. */
    public String randomSecret(int length) {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return sb.toString();
    }
}

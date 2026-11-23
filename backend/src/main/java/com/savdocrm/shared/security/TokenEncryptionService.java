package com.savdocrm.shared.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides authenticated encryption (AES-256-GCM) with key versioning,
 * HKDF key derivation (RFC 5869), and key rotation support for short-lived sensitive tokens.
 */
@Slf4j
@Service
public class TokenEncryptionService {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12; // 96 bits recommended for GCM
    private static final String HKDF_ALGORITHM = "HmacSHA256";
    private static final byte[] HKDF_SALT = "savdocrm-hkdf-token-encryption-salt-v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HKDF_INFO = "savdocrm-refresh-token-aes-256-gcm".getBytes(StandardCharsets.UTF_8);

    private final Map<String, SecretKey> keyRing = new ConcurrentHashMap<>();
    private volatile String activeKeyVersion = "v1";
    private final SecureRandom secureRandom;

    public TokenEncryptionService(JwtProperties jwtProperties) {
        this.secureRandom = new SecureRandom();

        String rawSecret = jwtProperties.getEncryptionSecret();
        if (rawSecret == null || rawSecret.trim().isBlank()) {
            log.warn("jwt.encryption-secret not explicitly configured. Deriving encryption key from jwt.secret via HKDF with domain separation.");
            rawSecret = jwtProperties.getSecret();
        }

        SecretKey initialKey = deriveHkdfKey(rawSecret, "v1");
        this.keyRing.put("v1", initialKey);
        this.activeKeyVersion = "v1";
    }

    /**
     * Encrypts plaintext using AES-256-GCM under the active key version.
     * Generates a unique 96-bit random IV per encryption.
     *
     * @param plaintext sensitive plaintext to encrypt
     * @return Versioned ciphertext string: "{version}:{Base64URL(IV || Ciphertext || Tag)}"
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }

        try {
            String version = this.activeKeyVersion;
            SecretKey key = keyRing.get(version);
            if (key == null) {
                throw new IllegalStateException("Active encryption key version '" + version + "' not found in keyring.");
            }

            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

            byte[] cipherTextWithTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherTextWithTag.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherTextWithTag);

            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(byteBuffer.array());
            return version + ":" + encodedPayload;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt token data", e);
        }
    }

    /**
     * Decrypts versioned ciphertext using AES-256-GCM.
     * Looks up the corresponding historical key version from the keyring to guarantee
     * active sessions are never broken during key rotation.
     *
     * @param encryptedData versioned ciphertext string
     * @return original plaintext
     */
    public String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isBlank()) {
            return null;
        }

        try {
            String version;
            String encodedPayload;

            int colonIndex = encryptedData.indexOf(':');
            if (colonIndex > 0) {
                version = encryptedData.substring(0, colonIndex);
                encodedPayload = encryptedData.substring(colonIndex + 1);
            } else {
                // Legacy unversioned payload backwards compatibility (defaults to v1)
                version = "v1";
                encodedPayload = encryptedData;
            }

            SecretKey key = keyRing.get(version);
            if (key == null) {
                throw new IllegalStateException("Encryption key version '" + version + "' not found in keyring. Active sessions cannot be decrypted without the corresponding historical key.");
            }

            byte[] decoded = Base64.getUrlDecoder().decode(encodedPayload);
            if (decoded.length < IV_LENGTH_BYTES + 16) {
                throw new IllegalArgumentException("Invalid encrypted payload length");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byteBuffer.get(iv);

            byte[] cipherTextWithTag = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherTextWithTag);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            byte[] plainTextBytes = cipher.doFinal(cipherTextWithTag);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt token data: ciphertext corrupted or invalid key", e);
        }
    }

    /**
     * Rotates active encryption key to a new version without breaking existing active sessions.
     * Previous keys remain registered in the keyring for decrypting existing tokens.
     *
     * @param newVersion the new key version identifier (e.g. "v2")
     * @param newSecret the new secret material
     */
    public synchronized void rotateKey(String newVersion, String newSecret) {
        if (newVersion == null || newVersion.isBlank()) {
            throw new IllegalArgumentException("New key version cannot be blank");
        }
        if (newSecret == null || newSecret.isBlank()) {
            throw new IllegalArgumentException("New key secret cannot be blank");
        }

        SecretKey newKey = deriveHkdfKey(newSecret, newVersion);
        this.keyRing.put(newVersion, newKey);
        this.activeKeyVersion = newVersion;
        log.info("Encryption key successfully rotated to version '{}'. Total keys in keyring: {}", newVersion, keyRing.size());
    }

    public String getActiveKeyVersion() {
        return this.activeKeyVersion;
    }

    public boolean hasKeyVersion(String version) {
        return this.keyRing.containsKey(version);
    }

    /**
     * Derives a 256-bit AES key using HKDF-SHA256 (RFC 5869) with domain-separated info.
     */
    private SecretKey deriveHkdfKey(String secret, String version) {
        try {
            byte[] ikm = secret.getBytes(StandardCharsets.UTF_8);
            byte[] info = (new String(HKDF_INFO, StandardCharsets.UTF_8) + ":" + version).getBytes(StandardCharsets.UTF_8);
            byte[] keyBytes = hkdfSha256(ikm, HKDF_SALT, info, 32);
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive AES-256 key via HKDF", e);
        }
    }

    private static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int length) {
        try {
            Mac mac = Mac.getInstance(HKDF_ALGORITHM);
            mac.init(new SecretKeySpec(salt, HKDF_ALGORITHM));
            byte[] prk = mac.doFinal(ikm);

            mac.init(new SecretKeySpec(prk, HKDF_ALGORITHM));
            ByteArrayOutputStream okm = new ByteArrayOutputStream();
            byte[] t = new byte[0];
            int iterations = (int) Math.ceil((double) length / 32);

            for (int i = 1; i <= iterations; i++) {
                mac.update(t);
                if (info != null && info.length > 0) {
                    mac.update(info);
                }
                mac.update((byte) i);
                t = mac.doFinal();
                okm.write(t);
            }

            byte[] fullOkm = okm.toByteArray();
            byte[] result = new byte[length];
            System.arraycopy(fullOkm, 0, result, 0, length);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("HKDF-SHA256 computation failed", e);
        }
    }
}

package com.savdocrm.shared.security;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Provides authenticated encryption (AES-256-GCM) for short-lived sensitive tokens,
 * such as successor refresh tokens stored during rotation grace windows.
 */
@Service
public class TokenEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public TokenEncryptionService(JwtProperties jwtProperties) {
        this.secretKey = deriveEncryptionKey(jwtProperties.getSecret());
        this.secureRandom = new SecureRandom();
    }

    /**
     * Encrypts plaintext using AES-256-GCM with a fresh random 96-bit IV.
     *
     * @param plaintext the plain text to encrypt
     * @return Base64-encoded string containing [IV (12 bytes) || Ciphertext || GCM Tag (16 bytes)]
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt token data", e);
        }
    }

    /**
     * Decrypts Base64-encoded ciphertext using AES-256-GCM and verifies authenticity.
     *
     * @param encryptedData Base64-encoded string containing [IV (12 bytes) || Ciphertext || GCM Tag]
     * @return original plaintext
     */
    public String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isBlank()) {
            return null;
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encryptedData);
            if (decoded.length < IV_LENGTH_BYTES + 16) {
                throw new IllegalArgumentException("Invalid encrypted payload length");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainTextBytes = cipher.doFinal(cipherText);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt token data: ciphertext corrupted or invalid key", e);
        }
    }

    private SecretKey deriveEncryptionKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] salt = "savdocrm-token-encryption-key-v1".getBytes(StandardCharsets.UTF_8);
            digest.update(salt);
            byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

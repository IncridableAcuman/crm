package com.savdocrm.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenEncryptionServiceTest {

    private JwtProperties jwtProperties;
    private TokenEncryptionService tokenEncryptionService;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("testJwtSecretKeyMustBeAtLeast32BytesLongForHmacSha256!");
        jwtProperties.setEncryptionSecret("dedicatedTokenEncryptionSecretKeyForAes256GcmTest!");
        tokenEncryptionService = new TokenEncryptionService(jwtProperties);
    }

    @Test
    @DisplayName("Should encrypt and decrypt plaintext using AES-256-GCM with version prefix")
    void shouldEncryptAndDecryptSuccessfully() {
        String originalToken = "raw-refresh-token-uuid-12345678-abcdef";

        String encrypted = tokenEncryptionService.encrypt(originalToken);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted).startsWith("v1:");
        assertThat(encrypted).isNotEqualTo(originalToken);

        String decrypted = tokenEncryptionService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(originalToken);
    }

    @Test
    @DisplayName("Should generate unique IV and distinct ciphertext for identical plaintext")
    void shouldProduceDifferentCiphertextDueToRandomIv() {
        String plaintext = "same-refresh-token-text";

        String ciphertext1 = tokenEncryptionService.encrypt(plaintext);
        String ciphertext2 = tokenEncryptionService.encrypt(plaintext);

        assertThat(ciphertext1).isNotEqualTo(ciphertext2);
        assertThat(tokenEncryptionService.decrypt(ciphertext1)).isEqualTo(plaintext);
        assertThat(tokenEncryptionService.decrypt(ciphertext2)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Should reject corrupted ciphertext or tampered authentication tag")
    void shouldFailOnCorruptedCiphertext() {
        String encrypted = tokenEncryptionService.encrypt("sensitive-token-material");

        // Tamper with the ciphertext body
        String tampered = encrypted.substring(0, encrypted.length() - 5) + "AAAAA";

        assertThatThrownBy(() -> tokenEncryptionService.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to decrypt token data");
    }

    @Test
    @DisplayName("Should rotate encryption key to v2 and decrypt historical tokens without breaking active sessions")
    void shouldSupportKeyRotationWithoutBreakingHistoricalSessions() {
        String tokenV1 = "token-created-under-key-v1";
        String ciphertextV1 = tokenEncryptionService.encrypt(tokenV1);

        assertThat(ciphertextV1).startsWith("v1:");

        // Rotate key to version v2
        tokenEncryptionService.rotateKey("v2", "completelyNewSecretForVersion2KeyRotationTest!");

        assertThat(tokenEncryptionService.getActiveKeyVersion()).isEqualTo("v2");
        assertThat(tokenEncryptionService.hasKeyVersion("v1")).isTrue();
        assertThat(tokenEncryptionService.hasKeyVersion("v2")).isTrue();

        // New tokens are encrypted under v2
        String tokenV2 = "token-created-under-key-v2";
        String ciphertextV2 = tokenEncryptionService.encrypt(tokenV2);
        assertThat(ciphertextV2).startsWith("v2:");

        // Crucial requirement: Existing active session tokens (v1) must decrypt successfully!
        String decryptedV1 = tokenEncryptionService.decrypt(ciphertextV1);
        assertThat(decryptedV1).isEqualTo(tokenV1);

        // v2 tokens also decrypt successfully
        String decryptedV2 = tokenEncryptionService.decrypt(ciphertextV2);
        assertThat(decryptedV2).isEqualTo(tokenV2);
    }

    @Test
    @DisplayName("Should support legacy unversioned ciphertext backwards compatibility")
    void shouldSupportLegacyUnversionedCiphertext() {
        String originalToken = "legacy-token-without-version-prefix";
        String versionedCiphertext = tokenEncryptionService.encrypt(originalToken);

        // Strip the "v1:" prefix to simulate pre-migration legacy data in PostgreSQL
        String legacyCiphertext = versionedCiphertext.substring(versionedCiphertext.indexOf(':') + 1);
        assertThat(legacyCiphertext).doesNotContain(":");

        String decrypted = tokenEncryptionService.decrypt(legacyCiphertext);
        assertThat(decrypted).isEqualTo(originalToken);
    }
}

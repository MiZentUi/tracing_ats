package ats.resume.infrastructure.vault;

import ats.resume.config.PiiProperties;
import ats.resume.domain.vault.EncryptedPii;
import ats.resume.domain.vault.PiiPayload;
import ats.resume.domain.vault.PiiVaultCrypto;
import ats.resume.infrastructure.json.JsonCodec;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** AES-256-GCM. AAD — id резюме, чтобы шифротекст нельзя было переставить между строками. */
@Component
@RequiredArgsConstructor
public class AesGcmPiiVaultCrypto implements PiiVaultCrypto {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final PiiProperties properties;
    private final JsonCodec jsonCodec;
    private final SecureRandom secureRandom = new SecureRandom();

    private byte[] key;

    @PostConstruct
    void init() {
        if (properties.getEncryptionKey() == null || properties.getEncryptionKey().isBlank()) {
            throw new IllegalStateException(
                    "ats.pii.encryption-key is required; PII must not be stored in plain text");
        }
        key = Base64.getDecoder().decode(properties.getEncryptionKey());
        if (key.length != 32) {
            throw new IllegalStateException(
                    "ats.pii.encryption-key must be a base64-encoded 32-byte AES-256 key");
        }
    }

    @Override
    public EncryptedPii encrypt(UUID resumeId, PiiPayload payload) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(resumeId.toString().getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext =
                    cipher.doFinal(jsonCodec.write(payload).getBytes(StandardCharsets.UTF_8));
            return new EncryptedPii(iv, ciphertext, properties.getKeyVersion());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot encrypt PII vault payload", e);
        }
    }

    @Override
    public PiiPayload decrypt(UUID resumeId, EncryptedPii encrypted) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, encrypted.iv()));
            cipher.updateAAD(resumeId.toString().getBytes(StandardCharsets.UTF_8));
            byte[] plaintext = cipher.doFinal(encrypted.ciphertext());
            return jsonCodec.read(new String(plaintext, StandardCharsets.UTF_8), PiiPayload.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot decrypt PII vault payload", e);
        }
    }
}

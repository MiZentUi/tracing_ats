package ats.resume.domain.vault;

import java.util.UUID;

/** Шифрование PII. AAD привязывает шифротекст к конкретному резюме. */
public interface PiiVaultCrypto {

    EncryptedPii encrypt(UUID resumeId, PiiPayload payload);

    PiiPayload decrypt(UUID resumeId, EncryptedPii encrypted);
}

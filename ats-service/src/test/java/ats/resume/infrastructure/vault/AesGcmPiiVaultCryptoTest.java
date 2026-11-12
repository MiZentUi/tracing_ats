package ats.resume.infrastructure.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ats.resume.config.PiiProperties;
import ats.resume.domain.privacy.PiiType;
import ats.resume.domain.vault.EncryptedPii;
import ats.resume.domain.vault.PiiPayload;
import ats.resume.infrastructure.json.JsonCodec;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AesGcmPiiVaultCryptoTest {

    private static final String KEY = "N3EUTRUDDeQVaKtCpzs3IYQTdYw8rQp/CSnETlKL5/U=";

    @Test
    void encryptsAndDecryptsWithResumeAsAad() {
        PiiProperties properties = new PiiProperties();
        properties.setEncryptionKey(KEY);
        AesGcmPiiVaultCrypto crypto = new AesGcmPiiVaultCrypto(properties, new JsonCodec());
        crypto.init();

        UUID resumeId = UUID.randomUUID();
        PiiPayload payload =
                new PiiPayload(
                        List.of(new PiiPayload.PiiEntry(PiiType.EMAIL, "[PII_EMAIL_1]", "a@b.c")),
                        "resume.pdf",
                        "https://github.com/example",
                        "University",
                        2022);

        EncryptedPii encrypted = crypto.encrypt(resumeId, payload);
        PiiPayload decrypted = crypto.decrypt(resumeId, encrypted);

        assertThat(decrypted.entries()).hasSize(1);
        assertThat(decrypted.entries().getFirst().value()).isEqualTo("a@b.c");
        assertThat(decrypted.originalFilename()).isEqualTo("resume.pdf");
        assertThatThrownBy(() -> crypto.decrypt(UUID.randomUUID(), encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsStartupWithoutKey() {
        PiiProperties properties = new PiiProperties();
        AesGcmPiiVaultCrypto crypto = new AesGcmPiiVaultCrypto(properties, new JsonCodec());

        assertThatThrownBy(crypto::init).isInstanceOf(IllegalStateException.class);
    }
}

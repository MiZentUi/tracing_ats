package ats.resume.domain.vault;

public record EncryptedPii(byte[] iv, byte[] ciphertext, String keyVersion) {

    public EncryptedPii {
        iv = iv.clone();
        ciphertext = ciphertext.clone();
    }

    @Override
    public byte[] iv() {
        return iv.clone();
    }

    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }
}

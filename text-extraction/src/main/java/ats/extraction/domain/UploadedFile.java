package ats.extraction.domain;

import java.util.Objects;

/**
 * Файл, пришедший на извлечение текста. Имя используется только как подсказка о формате
 * и не считается доверенным; содержимое копируется, чтобы вызывающий код не мог его изменить.
 */
public record UploadedFile(String fileName, byte[] content) {

    public UploadedFile {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(content, "content");
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}

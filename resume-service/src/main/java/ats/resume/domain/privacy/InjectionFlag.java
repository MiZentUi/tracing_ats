package ats.resume.domain.privacy;

/** Флаг удалённой инструкции. Исходный текст и его хеш-основание не хранятся. */
public record InjectionFlag(String rule, int line, String fragmentSha256) {}

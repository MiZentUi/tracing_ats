package ats.resume.domain.privacy;

/** Категории PII, которые маскируются до вызова scoring/LLM. */
public enum PiiType {
    NAME,
    EMAIL,
    PHONE,
    URL,
    TELEGRAM,
    LINKEDIN,
    GITHUB,
    PASSPORT,
    SNILS,
    INN,
    BIRTH_DATE,
    AGE,
    GENDER,
    LOCATION,
    UNIVERSITY,
    GRADUATION_YEAR,
    MARITAL_STATUS,
    PHOTO
}

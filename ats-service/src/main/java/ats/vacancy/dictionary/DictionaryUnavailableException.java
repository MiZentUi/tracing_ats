package ats.vacancy.dictionary;

/** dictionary-service недоступен или вернул транспортную ошибку. */
public class DictionaryUnavailableException extends RuntimeException {

    public DictionaryUnavailableException(String message) {
        super(message);
    }

    public DictionaryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

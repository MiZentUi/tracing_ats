package ats.vacancy.dictionary;

import io.grpc.Status;

/** Ошибка словаря, которую нужно показать клиенту (конфликт, не найдено, валидация). */
public class DictionaryRpcException extends RuntimeException {

    private final Status.Code code;

    public DictionaryRpcException(Status.Code code, String message) {
        super(message);
        this.code = code;
    }

    public Status.Code code() {
        return code;
    }
}

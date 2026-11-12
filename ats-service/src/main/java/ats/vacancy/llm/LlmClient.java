package ats.vacancy.llm;

public interface LlmClient {

    /**
     * Выполняет один логический вызов: повторы, запасные модели и общий дедлайн скрыты за
     * интерфейсом.
     */
    LlmResponse complete(LlmRequest request);
}

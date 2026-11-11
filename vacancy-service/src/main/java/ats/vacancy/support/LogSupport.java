package ats.vacancy.support;

/**
 * Компактные фрагменты для логов: без полного текста промптов, резюме и вакансий. Цепочка cause
 * печатается одной строкой, чтобы WARN не превращался в километровый стектрейс.
 */
public final class LogSupport {

    private static final int MAX_CAUSE_DEPTH = 5;
    private static final int MAX_MESSAGE_CHARS = 300;

    private LogSupport() {}

    public static String describe(Throwable throwable) {
        if (throwable == null) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (depth > 0) {
                builder.append(" <- ");
            }
            builder.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                builder.append(": ").append(shorten(message));
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    /** Короткий фрагмент ответа LLM для диагностики невалидного JSON. */
    public static String excerpt(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").strip();
        return compact.length() <= maxChars ? compact : compact.substring(0, maxChars) + "…";
    }

    private static String shorten(String message) {
        String compact = message.replaceAll("\\s+", " ").strip();
        return compact.length() <= MAX_MESSAGE_CHARS
                ? compact
                : compact.substring(0, MAX_MESSAGE_CHARS) + "…";
    }
}

package ats.vacancy.template;

public final class QuoteMatcher {

    private QuoteMatcher() {}

    /** Схлопывает любые пробельные последовательности, включая NBSP и narrow NBSP. */
    public static String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\s\\u00A0\\u202F]+", " ").strip();
    }

    public static boolean isSubstring(String quote, String text) {
        String normalizedQuote = normalizeWhitespace(quote);
        return !normalizedQuote.isEmpty() && normalizeWhitespace(text).contains(normalizedQuote);
    }
}

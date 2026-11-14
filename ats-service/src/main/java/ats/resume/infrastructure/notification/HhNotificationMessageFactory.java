package ats.resume.infrastructure.notification;

import ats.resume.config.TelegramProperties;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Текст алерта: вакансия, Score_pre и ссылка на анализ, без имени и контактов кандидата. */
@Component
@RequiredArgsConstructor
public class HhNotificationMessageFactory {

    private final TelegramProperties properties;

    public String scored(
            NotificationOutboxService.HhScoreNotificationPayload payload, UUID applicationId) {
        StringBuilder message =
                new StringBuilder("Новый отклик с hh.ru\n")
                        .append("Вакансия: ")
                        .append(safeTitle(payload.vacancyTitle()))
                        .append("\nПредварительный скор (Score_pre): ")
                        .append(number(payload.score()));
        String link = analysisLink(applicationId);
        if (link != null) {
            message.append("\nАнализ: ").append(link);
        }
        return message.toString();
    }

    public String unmatched(NotificationOutboxService.HhUnmatchedPayload payload) {
        return "Отклик с hh.ru по вакансии "
                + payload.hhVacancyId()
                + " не обработан: вакансия не привязана к платформе.";
    }

    /** Ссылка на отчёт по конкретной паре резюме–вакансия, чтобы HR не искал её в системе. */
    private String analysisLink(UUID applicationId) {
        String baseUrl = properties.getFrontendBaseUrl();
        if (applicationId == null || baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        int end = baseUrl.length();
        while (end > 0 && baseUrl.charAt(end - 1) == '/') {
            end--;
        }
        return baseUrl.substring(0, end)
                + "/applications/"
                + applicationId
                + "/report?phase=PRELIMINARY";
    }

    private static String safeTitle(String title) {
        return title == null || title.isBlank() ? "без названия" : title.strip();
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', ',');
    }
}

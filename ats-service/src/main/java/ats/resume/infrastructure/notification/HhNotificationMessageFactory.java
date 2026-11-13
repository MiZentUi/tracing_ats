package ats.resume.infrastructure.notification;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Текст алерта: только вакансия и скор, без имени и контактов кандидата. */
@Component
public class HhNotificationMessageFactory {

    public String scored(NotificationOutboxService.HhScoreNotificationPayload payload) {
        return "Новый отклик с hh.ru\n"
                + "Вакансия: "
                + safeTitle(payload.vacancyTitle())
                + "\nПредварительный скор (Score_pre): "
                + number(payload.score())
                + " ("
                + number(payload.scoreLow())
                + "–"
                + number(payload.scoreHigh())
                + ")";
    }

    public String unmatched(NotificationOutboxService.HhUnmatchedPayload payload) {
        return "Отклик с hh.ru по вакансии "
                + payload.hhVacancyId()
                + " не обработан: вакансия не привязана к платформе.";
    }

    private static String safeTitle(String title) {
        return title == null || title.isBlank() ? "без названия" : title.strip();
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', ',');
    }
}

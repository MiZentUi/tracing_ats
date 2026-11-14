package ats.resume.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;

import ats.resume.config.TelegramProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HhNotificationMessageFactoryTest {

    private static final UUID APPLICATION_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final TelegramProperties properties = new TelegramProperties();
    private final HhNotificationMessageFactory factory =
            new HhNotificationMessageFactory(properties);

    @Test
    void messageContainsVacancyScoreAndAnalysisLinkButNoBandOrIdentity() {
        properties.setFrontendBaseUrl("https://ats.example.com/");

        String message =
                factory.scored(
                        new NotificationOutboxService.HhScoreNotificationPayload(
                                UUID.randomUUID(), "Backend Developer", 69.0, 62.0, 82.0),
                        APPLICATION_ID);

        assertThat(message)
                .contains("Backend Developer")
                .contains("Предварительный скор (Score_pre): 69")
                .contains(
                        "Анализ: https://ats.example.com/applications/"
                                + APPLICATION_ID
                                + "/report?phase=PRELIMINARY")
                .doesNotContain("62")
                .doesNotContain("82")
                .doesNotContain("–")
                .doesNotContain("Иван")
                .doesNotContain("email")
                .doesNotContain("phone");
    }

    @Test
    void messageOmitsAnalysisLinkWhenApplicationIsUnknown() {
        String message =
                factory.scored(
                        new NotificationOutboxService.HhScoreNotificationPayload(
                                UUID.randomUUID(), "Backend Developer", 69.0, 62.0, 82.0),
                        null);

        assertThat(message).doesNotContain("Анализ:");
    }

    @Test
    void unmatchedMessageContainsOnlyHhVacancyId() {
        String message =
                factory.unmatched(new NotificationOutboxService.HhUnmatchedPayload("12345678"));

        assertThat(message).contains("12345678").contains("не привязана");
    }
}

package ats.resume.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class HhNotificationMessageFactoryTest {

    private final HhNotificationMessageFactory factory = new HhNotificationMessageFactory();

    @Test
    void messageContainsVacancyAndScoreButNoCandidateIdentity() {
        String message =
                factory.scored(
                        new NotificationOutboxService.HhScoreNotificationPayload(
                                UUID.randomUUID(), "Backend Developer", 69.0, 62.0, 82.0));

        assertThat(message)
                .contains("Backend Developer")
                .contains("Score_pre")
                .contains("69")
                .contains("62")
                .contains("82")
                .doesNotContain("Иван")
                .doesNotContain("email")
                .doesNotContain("phone");
    }

    @Test
    void unmatchedMessageContainsOnlyHhVacancyId() {
        String message =
                factory.unmatched(new NotificationOutboxService.HhUnmatchedPayload("12345678"));

        assertThat(message).contains("12345678").contains("не привязана");
    }
}

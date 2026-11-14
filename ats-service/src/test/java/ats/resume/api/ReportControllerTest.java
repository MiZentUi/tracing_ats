package ats.resume.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import ats.gateway.model.CandidateMessage;
import ats.gateway.model.CandidateMessageKind;
import ats.gateway.model.CandidateMessageRequest;
import ats.resume.infrastructure.report.CandidateMessageService;
import ats.resume.infrastructure.report.ReportDecisionService;
import ats.resume.infrastructure.report.ReportService;
import ats.resume.infrastructure.report.RevealService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final UUID RESUME_ID = UUID.randomUUID();
    private static final UUID VACANCY_ID = UUID.randomUUID();

    @Mock private ReportService reportService;
    @Mock private ReportDecisionService decisionService;
    @Mock private RevealService revealService;
    @Mock private CandidateMessageService candidateMessageService;

    private ReportController controller;

    @BeforeEach
    void setUp() {
        controller =
                new ReportController(
                        reportService, decisionService, revealService, candidateMessageService);
    }

    @Test
    void mapsCandidateMessageResponse() {
        CandidateMessageService.View view =
                new CandidateMessageService.View(
                        UUID.randomUUID(),
                        APPLICATION_ID,
                        RESUME_ID,
                        VACANCY_ID,
                        "FINAL",
                        "REJECTION",
                        "Тема",
                        "Тело письма",
                        "candidate-message.rejection.v1",
                        "model",
                        Instant.parse("2026-11-14T12:00:00Z"));
        when(candidateMessageService.generate(
                        eq(APPLICATION_ID), eq("FINAL"), eq(CandidateMessageKind.REJECTION)))
                .thenReturn(view);

        ResponseEntity<CandidateMessage> response =
                controller.generateCandidateMessage(
                        APPLICATION_ID,
                        new CandidateMessageRequest(CandidateMessageKind.REJECTION),
                        "FINAL");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getKind()).isEqualTo(CandidateMessageKind.REJECTION);
        assertThat(response.getBody().getPhase()).isEqualTo(CandidateMessage.PhaseEnum.FINAL);
        assertThat(response.getBody().getSubject()).isEqualTo("Тема");
        assertThat(response.getBody().getCreatedAt())
                .isEqualTo(view.createdAt().atOffset(java.time.ZoneOffset.UTC));
    }

    @Test
    void returnsLatestDrafts() {
        CandidateMessageService.View view =
                new CandidateMessageService.View(
                        UUID.randomUUID(),
                        APPLICATION_ID,
                        RESUME_ID,
                        VACANCY_ID,
                        "PRELIMINARY",
                        "INTERVIEW_INVITATION",
                        "Тема",
                        "Тело письма",
                        "candidate-message.invitation.v1",
                        null,
                        Instant.parse("2026-11-14T12:00:00Z"));
        when(candidateMessageService.latest(APPLICATION_ID, "PRELIMINARY"))
                .thenReturn(List.of(view));

        ResponseEntity<List<CandidateMessage>> response =
                controller.getCandidateMessages(APPLICATION_ID, "PRELIMINARY");

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().getFirst().getKind())
                .isEqualTo(CandidateMessageKind.INTERVIEW_INVITATION);
    }

    @Test
    void mapsResumeVacancyCandidateMessageResponse() {
        CandidateMessageService.View view =
                new CandidateMessageService.View(
                        UUID.randomUUID(),
                        null,
                        RESUME_ID,
                        VACANCY_ID,
                        "PRELIMINARY",
                        "INTERVIEW_INVITATION",
                        "Тема",
                        "Тело письма",
                        "candidate-message.invitation.v1",
                        "model",
                        Instant.parse("2026-11-14T12:00:00Z"));
        when(candidateMessageService.generateForMatch(
                        RESUME_ID, VACANCY_ID, CandidateMessageKind.INTERVIEW_INVITATION))
                .thenReturn(view);

        ResponseEntity<CandidateMessage> response =
                controller.generateResumeVacancyCandidateMessage(
                        RESUME_ID,
                        VACANCY_ID,
                        new CandidateMessageRequest(CandidateMessageKind.INTERVIEW_INVITATION));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getApplicationId()).isNull();
        assertThat(response.getBody().getResumeId()).isEqualTo(RESUME_ID);
        assertThat(response.getBody().getVacancyId()).isEqualTo(VACANCY_ID);
    }
}

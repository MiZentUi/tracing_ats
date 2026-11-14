package ats.resume.infrastructure.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ats.gateway.model.CandidateMessageKind;
import ats.resume.infrastructure.json.JsonCodec;
import ats.resume.infrastructure.persistence.entity.AuditEventEntity;
import ats.resume.infrastructure.persistence.entity.CandidateMessageEntity;
import ats.resume.infrastructure.persistence.repository.AuditEventRepository;
import ats.resume.infrastructure.persistence.repository.CandidateMessageRepository;
import ats.resume.infrastructure.report.ReportService.ReportGap;
import ats.resume.infrastructure.report.ReportService.ReportView;
import ats.resume.infrastructure.report.ReportService.RequirementReportItem;
import ats.vacancy.application.ApiException;
import ats.vacancy.llm.LlmClient;
import ats.vacancy.llm.LlmResponse;
import ats.vacancy.llm.LlmUnavailableException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CandidateMessageServiceTest {

    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final UUID RUN_ID = UUID.randomUUID();

    private final Logger logger = (Logger) LoggerFactory.getLogger(CandidateMessageService.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @Mock private ReportService reportService;
    @Mock private CandidateMessageRepository messageRepository;
    @Mock private CandidateMessagePrompt prompt;
    @Mock private CandidateMessageParser parser;
    @Mock private LlmClient llmClient;
    @Mock private AuditEventRepository auditEventRepository;

    private CandidateMessageService service;

    @BeforeEach
    void setUp() {
        service =
                new CandidateMessageService(
                        reportService,
                        messageRepository,
                        prompt,
                        parser,
                        llmClient,
                        auditEventRepository,
                        new JsonCodec());
        appender.start();
        logger.addAppender(appender);

        when(prompt.prompt(any()))
                .thenAnswer(
                        invocation ->
                                new CandidateMessagePrompt.Prompt(
                                        invocation.getArgument(0) == CandidateMessageKind.REJECTION
                                                ? CandidateMessagePrompt.REJECTION_VERSION
                                                : CandidateMessagePrompt.INVITATION_VERSION,
                                        "system",
                                        "{}"));
        when(prompt.userPrompt(any()))
                .thenReturn("<candidate_message_data>{}</candidate_message_data>");
        when(messageRepository.saveAndFlush(any()))
                .thenAnswer(
                        invocation -> {
                            CandidateMessageEntity entity = invocation.getArgument(0);
                            entity.setId(UUID.randomUUID());
                            entity.setCreatedAt(Instant.now());
                            return entity;
                        });
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    @Test
    void generatesRejectionFromUncoveredRequirementsOnly() {
        ReportView report =
                report(
                        List.of(requirement("req-1", "Java", true, "GAP")),
                        List.of(new ReportGap("req-1", "Java", "INSUFFICIENT_TENURE")));
        when(reportService.report(APPLICATION_ID, "FINAL")).thenReturn(Optional.of(report));
        when(llmClient.complete(any()))
                .thenReturn(
                        new LlmResponse(
                                json("Тема отказа", "Тело письма. ".repeat(15)),
                                "model",
                                1,
                                1,
                                "stop"));
        when(parser.parse(anyString()))
                .thenReturn(
                        new CandidateMessageParser.ParsedMessage(
                                "Тема отказа", "Тело письма. ".repeat(15)));

        CandidateMessageService.View view =
                service.generate(APPLICATION_ID, "FINAL", CandidateMessageKind.REJECTION);

        assertThat(view.kind()).isEqualTo("REJECTION");
        assertThat(view.promptVersion()).isEqualTo(CandidateMessagePrompt.REJECTION_VERSION);
        assertThat(view.subject()).isEqualTo("Тема отказа");
        assertThat(view.applicationId()).isEqualTo(APPLICATION_ID);
        assertThat(view.createdAt()).isNotNull();
        verify(auditEventRepository).save(any(AuditEventEntity.class));

        ArgumentCaptor<Object> data = ArgumentCaptor.forClass(Object.class);
        verify(prompt).userPrompt(data.capture());
        assertThat(data.getValue().toString())
                .contains("uncovered_requirements")
                .contains("Java")
                .doesNotContain("resume")
                .doesNotContain("Иван");

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(m -> m.startsWith("candidate_message_generation_started"))
                .anyMatch(m -> m.startsWith("candidate_message_generation_finished"))
                .noneMatch(m -> m.contains("Тело письма"));
    }

    @Test
    void generatesInvitationFromConfirmedRequirements() {
        ReportView report =
                report(
                        List.of(
                                requirement("req-1", "Java", true, "COVERED"),
                                requirement("req-2", "Kafka", false, "GAP")),
                        List.of(new ReportGap("req-2", "Kafka", "NOT_STATED")));
        when(reportService.report(APPLICATION_ID, "FINAL")).thenReturn(Optional.of(report));
        when(llmClient.complete(any()))
                .thenReturn(
                        new LlmResponse(
                                json("Тема", "Тело письма. ".repeat(15)), "model", 1, 1, "stop"));
        when(parser.parse(anyString()))
                .thenReturn(
                        new CandidateMessageParser.ParsedMessage(
                                "Тема", "Тело письма. ".repeat(15)));

        CandidateMessageService.View view =
                service.generate(
                        APPLICATION_ID, "FINAL", CandidateMessageKind.INTERVIEW_INVITATION);

        assertThat(view.kind()).isEqualTo("INTERVIEW_INVITATION");
        assertThat(view.promptVersion()).isEqualTo(CandidateMessagePrompt.INVITATION_VERSION);
        ArgumentCaptor<Object> data = ArgumentCaptor.forClass(Object.class);
        verify(prompt).userPrompt(data.capture());
        assertThat(data.getValue().toString())
                .contains("confirmed_requirements")
                .contains("Java")
                .doesNotContain("Kafka")
                .doesNotContain("uncovered_requirements");
    }

    @Test
    void refusesRejectionWhenThereAreNoGaps() {
        ReportView report = report(List.of(), List.of());
        when(reportService.report(APPLICATION_ID, "FINAL")).thenReturn(Optional.of(report));

        assertThatThrownBy(
                        () ->
                                service.generate(
                                        APPLICATION_ID, "FINAL", CandidateMessageKind.REJECTION))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.status().value()).isEqualTo(422);
                            assertThat(exception.code()).isEqualTo("NO_UNCOVERED_REQUIREMENTS");
                        });
        verifyNoInteractions(llmClient);
    }

    @Test
    void retriesOnceWhenResponseIsInvalid() {
        ReportView report =
                report(
                        List.of(requirement("req-1", "Java", true, "GAP")),
                        List.of(new ReportGap("req-1", "Java", "NOT_STATED")));
        when(reportService.report(APPLICATION_ID, "FINAL")).thenReturn(Optional.of(report));
        when(llmClient.complete(any()))
                .thenReturn(
                        new LlmResponse(
                                json("Тема", "Тело письма. ".repeat(15)), "model", 1, 1, "stop"));
        when(parser.parse(anyString()))
                .thenThrow(new CandidateMessageParseException("bad"))
                .thenReturn(
                        new CandidateMessageParser.ParsedMessage(
                                "Тема", "Тело письма. ".repeat(15)));

        CandidateMessageService.View view =
                service.generate(APPLICATION_ID, "FINAL", CandidateMessageKind.REJECTION);

        assertThat(view.subject()).isEqualTo("Тема");
        verify(llmClient, org.mockito.Mockito.times(2)).complete(any());
    }

    @Test
    void failsAfterSecondInvalidResponse() {
        ReportView report =
                report(
                        List.of(requirement("req-1", "Java", true, "GAP")),
                        List.of(new ReportGap("req-1", "Java", "NOT_STATED")));
        when(reportService.report(APPLICATION_ID, "FINAL")).thenReturn(Optional.of(report));
        when(llmClient.complete(any()))
                .thenReturn(
                        new LlmResponse(
                                json("Тема", "Тело письма. ".repeat(15)), "model", 1, 1, "stop"));
        when(parser.parse(anyString())).thenThrow(new CandidateMessageParseException("bad"));

        assertThatThrownBy(
                        () ->
                                service.generate(
                                        APPLICATION_ID, "FINAL", CandidateMessageKind.REJECTION))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.status().value()).isEqualTo(502);
                            assertThat(exception.code()).isEqualTo("CANDIDATE_MESSAGE_FAILED");
                        });
    }

    @Test
    void mapsLlmFailureToBadGateway() {
        ReportView report =
                report(
                        List.of(requirement("req-1", "Java", true, "GAP")),
                        List.of(new ReportGap("req-1", "Java", "NOT_STATED")));
        when(reportService.report(APPLICATION_ID, "FINAL")).thenReturn(Optional.of(report));
        when(llmClient.complete(any())).thenThrow(new LlmUnavailableException("down", null));

        assertThatThrownBy(
                        () ->
                                service.generate(
                                        APPLICATION_ID, "FINAL", CandidateMessageKind.REJECTION))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> {
                            assertThat(exception.status().value()).isEqualTo(502);
                            assertThat(exception.code()).isEqualTo("CANDIDATE_MESSAGE_FAILED");
                        });
    }

    @Test
    void returnsLatestMessagePerKind() {
        CandidateMessageEntity older =
                entity(CandidateMessageKind.REJECTION, "Старое", Instant.now().minusSeconds(10));
        CandidateMessageEntity newer =
                entity(CandidateMessageKind.REJECTION, "Новое", Instant.now());
        when(reportService.report(APPLICATION_ID, "FINAL"))
                .thenReturn(Optional.of(report(List.of(), List.of())));
        when(messageRepository.findByScoreRunIdOrderByCreatedAtDesc(RUN_ID))
                .thenReturn(List.of(newer, older));

        List<CandidateMessageService.View> views = service.latest(APPLICATION_ID, "FINAL");

        assertThat(views).hasSize(1);
        assertThat(views.getFirst().subject()).isEqualTo("Новое");
        verifyNoInteractions(llmClient);
    }

    private ReportView report(List<RequirementReportItem> requirements, List<ReportGap> gaps) {
        return new ReportView(
                APPLICATION_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                RUN_ID,
                "Java-разработчик",
                "FINAL",
                0.8,
                69.0,
                62.0,
                82.0,
                false,
                "SHORTLISTED",
                requirements,
                List.of(),
                List.of(),
                true,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                "note",
                gaps);
    }

    private RequirementReportItem requirement(
            String id, String text, boolean critical, String status) {
        return new RequirementReportItem(
                id, text, "MUST", critical, 0.8, 0.7, 3, status, null, null, null);
    }

    private CandidateMessageEntity entity(
            CandidateMessageKind kind, String subject, Instant createdAt) {
        CandidateMessageEntity entity = new CandidateMessageEntity();
        entity.setId(UUID.randomUUID());
        entity.setApplicationId(APPLICATION_ID);
        entity.setScoreRunId(RUN_ID);
        entity.setPhase("FINAL");
        entity.setKind(kind.name());
        entity.setSubject(subject);
        entity.setBody("Тело письма. ".repeat(15));
        entity.setPromptVersion("test");
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private static String json(String subject, String body) {
        return "{\"subject\":\"" + subject + "\",\"body\":\"" + body + "\"}";
    }
}

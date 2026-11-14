package ats.resume.infrastructure.report;

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
import ats.vacancy.llm.LlmException;
import ats.vacancy.llm.LlmRequest;
import ats.vacancy.llm.LlmResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Генерация черновиков писем кандидату. В LLM не передаются PII и текст резюме. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateMessageService {

    private static final String ONLY_JSON =
            "Верни только один JSON-объект по схеме, без текста вокруг.";
    private static final int MAX_FACTS = 5;

    private final ReportService reportService;
    private final CandidateMessageRepository messageRepository;
    private final CandidateMessagePrompt prompt;
    private final CandidateMessageParser parser;
    private final LlmClient llmClient;
    private final AuditEventRepository auditEventRepository;
    private final JsonCodec jsonCodec;

    /** Последние черновики по каждому типу для текущего запуска. */
    public List<View> latest(UUID applicationId, String phase) {
        ReportView report = report(applicationId, phase);
        Map<String, CandidateMessageEntity> latestByKind = new LinkedHashMap<>();
        for (CandidateMessageEntity entity :
                messageRepository.findByScoreRunIdOrderByCreatedAtDesc(report.scoreRunId())) {
            latestByKind.putIfAbsent(entity.getKind(), entity);
        }
        return latestByKind.values().stream()
                .sorted(
                        Comparator.comparingInt(
                                entity -> CandidateMessageKind.valueOf(entity.getKind()).ordinal()))
                .map(this::view)
                .toList();
    }

    public View generate(UUID applicationId, String phase, CandidateMessageKind kind) {
        ReportView report = report(applicationId, phase);
        List<String> confirmed =
                kind == CandidateMessageKind.INTERVIEW_INVITATION
                        ? confirmedRequirements(report)
                        : List.of();
        List<UncoveredRequirement> uncovered =
                kind == CandidateMessageKind.REJECTION ? uncoveredRequirements(report) : List.of();

        if (kind == CandidateMessageKind.REJECTION && uncovered.isEmpty()) {
            log.info(
                    "candidate_message_generation_rejected application_id={} phase={} kind={}"
                            + " reason=no_uncovered_requirements",
                    applicationId,
                    report.phase(),
                    kind);
            throw ApiException.unprocessable(
                    "NO_UNCOVERED_REQUIREMENTS",
                    "Rejection letter requires uncovered requirements");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kind", kind.name());
        data.put("vacancy_title", report.vacancyTitle() == null ? "" : report.vacancyTitle());
        if (kind == CandidateMessageKind.INTERVIEW_INVITATION) {
            data.put("confirmed_requirements", confirmed);
        } else {
            data.put(
                    "uncovered_requirements",
                    uncovered.stream()
                            .map(
                                    fact ->
                                            Map.of(
                                                    "text", fact.text(),
                                                    "reason", fact.reason()))
                            .toList());
        }

        CandidateMessagePrompt.Prompt selected = prompt.prompt(kind);
        LlmRequest request =
                new LlmRequest(
                        selected.systemPrompt(), prompt.userPrompt(data), selected.jsonSchema());

        long startedAt = System.nanoTime();
        int facts =
                kind == CandidateMessageKind.INTERVIEW_INVITATION
                        ? confirmed.size()
                        : uncovered.size();
        log.info(
                "candidate_message_generation_started application_id={} phase={} kind={}"
                        + " facts={} prompt_version={}",
                applicationId,
                report.phase(),
                kind,
                facts,
                selected.version());

        LlmResponse response = call(request);
        ParsedWithResponse parsed = parseWithRetry(request, response);
        response = parsed.response();

        CandidateMessageEntity entity = new CandidateMessageEntity();
        entity.setApplicationId(applicationId);
        entity.setScoreRunId(report.scoreRunId());
        entity.setPhase(report.phase());
        entity.setKind(kind.name());
        entity.setSubject(parsed.message().subject());
        entity.setBody(parsed.message().body());
        entity.setPromptVersion(selected.version());
        entity.setModelVersion(hasText(response.model()) ? response.model() : null);
        entity = messageRepository.saveAndFlush(entity);
        audit(entity);

        log.info(
                "candidate_message_generation_finished application_id={} run_id={} kind={}"
                        + " prompt_version={} model={} subject_chars={} body_chars={}"
                        + " elapsed_ms={}",
                applicationId,
                report.scoreRunId(),
                kind,
                selected.version(),
                entity.getModelVersion(),
                entity.getSubject().length(),
                entity.getBody().length(),
                elapsedMs(startedAt));
        return view(entity);
    }

    private ReportView report(UUID applicationId, String phase) {
        return reportService
                .report(applicationId, phase)
                .orElseThrow(() -> ApiException.notFound("Application report not found"));
    }

    private LlmResponse call(LlmRequest request) {
        try {
            return llmClient.complete(request);
        } catch (LlmException e) {
            log.warn("candidate_message_generation_failed error={}", e.getClass().getSimpleName());
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "CANDIDATE_MESSAGE_FAILED",
                    "Candidate message generation failed");
        }
    }

    private ParsedWithResponse parseWithRetry(LlmRequest request, LlmResponse response) {
        try {
            return new ParsedWithResponse(parser.parse(response.content()), response);
        } catch (CandidateMessageParseException firstFailure) {
            log.warn(
                    "candidate_message_response_invalid attempt=1 reason={} content_chars={}",
                    firstFailure.getMessage(),
                    response.content() == null ? 0 : response.content().length());
            LlmResponse retryResponse =
                    call(
                            new LlmRequest(
                                    request.systemPrompt(),
                                    request.userPrompt() + "\n\n" + ONLY_JSON,
                                    request.jsonSchema()));
            try {
                return new ParsedWithResponse(parser.parse(retryResponse.content()), retryResponse);
            } catch (CandidateMessageParseException secondFailure) {
                log.warn(
                        "candidate_message_response_invalid attempt=2 reason={}"
                                + " first_reason={} content_chars={}",
                        secondFailure.getMessage(),
                        firstFailure.getMessage(),
                        retryResponse.content() == null ? 0 : retryResponse.content().length());
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "CANDIDATE_MESSAGE_FAILED",
                        "Candidate message generation failed");
            }
        }
    }

    private List<String> confirmedRequirements(ReportView report) {
        return report.requirements().stream()
                .filter(item -> "COVERED".equals(item.status()))
                .filter(item -> hasText(item.text()))
                .sorted(requirementOrder())
                .limit(MAX_FACTS)
                .map(RequirementReportItem::text)
                .toList();
    }

    private List<UncoveredRequirement> uncoveredRequirements(ReportView report) {
        Map<String, RequirementReportItem> byId =
                report.requirements().stream()
                        .collect(
                                Collectors.toMap(
                                        RequirementReportItem::requirementId,
                                        Function.identity(),
                                        (left, right) -> left));
        return report.gaps().stream()
                .filter(Objects::nonNull)
                .sorted(gapOrder(byId))
                .map(gap -> uncovered(gap, byId.get(gap.requirementId())))
                .filter(Objects::nonNull)
                .limit(MAX_FACTS)
                .toList();
    }

    private UncoveredRequirement uncovered(ReportGap gap, RequirementReportItem requirement) {
        String text =
                hasText(gap.text()) ? gap.text() : requirement == null ? null : requirement.text();
        if (!hasText(text)) {
            return null;
        }
        return new UncoveredRequirement(text, reasonLabel(gap.reason()));
    }

    private Comparator<RequirementReportItem> requirementOrder() {
        return Comparator.comparing(RequirementReportItem::critical)
                .reversed()
                .thenComparing(RequirementReportItem::requirementId);
    }

    private Comparator<ReportGap> gapOrder(Map<String, RequirementReportItem> byId) {
        return Comparator.comparing(
                        (ReportGap gap) -> {
                            RequirementReportItem item = byId.get(gap.requirementId());
                            return item != null && item.critical();
                        })
                .reversed()
                .thenComparing(ReportGap::requirementId);
    }

    private String reasonLabel(String reason) {
        if (reason == null) {
            return "требование не подтверждено";
        }
        return switch (reason) {
            case "NOT_STATED" -> "в резюме нет сведений";
            case "ABSENT" -> "в резюме есть сведения, но требование не подтверждено";
            case "PARTIAL" -> "соответствие подтверждено частично";
            case "INSUFFICIENT_TENURE" -> "подтверждённого стажа меньше требуемого";
            case "CONTRADICTION" -> "данные в резюме противоречат друг другу";
            default -> "требование не подтверждено";
        };
    }

    private void audit(CandidateMessageEntity entity) {
        AuditEventEntity audit = new AuditEventEntity();
        audit.setApplicationId(entity.getApplicationId());
        audit.setActor("hr");
        audit.setAction("CANDIDATE_MESSAGE_GENERATED");
        audit.setReason("candidate message generated for HR review");
        audit.setMetadata(
                jsonCodec.write(
                        Map.of(
                                "applicationId", entity.getApplicationId().toString(),
                                "scoreRunId", entity.getScoreRunId().toString(),
                                "kind", entity.getKind(),
                                "promptVersion", entity.getPromptVersion(),
                                "model",
                                        entity.getModelVersion() == null
                                                ? ""
                                                : entity.getModelVersion())));
        auditEventRepository.save(audit);
    }

    private View view(CandidateMessageEntity entity) {
        return new View(
                entity.getId(),
                entity.getApplicationId(),
                entity.getPhase(),
                entity.getKind(),
                entity.getSubject(),
                entity.getBody(),
                entity.getPromptVersion(),
                entity.getModelVersion(),
                entity.getCreatedAt());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private record UncoveredRequirement(String text, String reason) {}

    private record ParsedWithResponse(
            CandidateMessageParser.ParsedMessage message, LlmResponse response) {}

    public record View(
            UUID id,
            UUID applicationId,
            String phase,
            String kind,
            String subject,
            String body,
            String promptVersion,
            String model,
            Instant createdAt) {}
}

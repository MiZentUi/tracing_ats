package ats.resume.infrastructure.ranking;

import ats.resume.infrastructure.json.JsonCodec;
import ats.resume.infrastructure.persistence.entity.ApplicationEntity;
import ats.resume.infrastructure.persistence.entity.ResumeEntity;
import ats.resume.infrastructure.persistence.entity.ScoreRunEntity;
import ats.resume.infrastructure.persistence.repository.ApplicationRepository;
import ats.resume.infrastructure.persistence.repository.ResumeRepository;
import ats.resume.infrastructure.persistence.repository.ScoreRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Рейтинг по вакансии. Для откликов используются актуальные указатели приложения, а для автоподбора
 * «резюме → вакансии» — последние обратные запуски. PRELIMINARY и FINAL не смешиваются: клиент
 * запрашивает нужную фазу и сам решает, что показать.
 */
@Service
@RequiredArgsConstructor
public class RankingService {

    private static final String REVERSE_DIRECTION = "RESUME_TO_VACANCIES";

    private final ApplicationRepository applicationRepository;
    private final ScoreRunRepository scoreRunRepository;
    private final ResumeRepository resumeRepository;
    private final JsonCodec jsonCodec;

    public RankingView ranking(UUID vacancyId, String phase) {
        String normalizedPhase = normalizePhase(phase);
        List<ApplicationEntity> applications = applicationRepository.findByVacancyId(vacancyId);

        Map<UUID, ScoreRunEntity> runsById = new LinkedHashMap<>();
        Map<UUID, UUID> runIdByApplication = new LinkedHashMap<>();
        for (ApplicationEntity application : applications) {
            UUID runId =
                    "FINAL".equals(normalizedPhase)
                            ? application.getFinalScoreRunId()
                            : application.getPreliminaryScoreRunId();
            if (runId != null) {
                runIdByApplication.put(application.getId(), runId);
            }
        }
        scoreRunRepository
                .findAllById(runIdByApplication.values())
                .forEach(run -> runsById.put(run.getId(), run));

        List<RankingItem> items = new ArrayList<>();
        Set<UUID> seenResumes = new HashSet<>();
        for (ApplicationEntity application : applications) {
            UUID runId = runIdByApplication.get(application.getId());
            ScoreRunEntity run = runId == null ? null : runsById.get(runId);
            if (run == null) {
                continue;
            }
            seenResumes.add(application.getResumeId());
            items.add(
                    item(
                            0,
                            application.getId(),
                            application.getResumeId(),
                            run,
                            application.getScreeningDecision(),
                            application.getSource()));
        }

        if ("PRELIMINARY".equals(normalizedPhase)) {
            Map<UUID, ScoreRunEntity> latestByResume = new LinkedHashMap<>();
            for (ScoreRunEntity run :
                    scoreRunRepository.findByVacancyIdAndDirectionAndPhaseOrderByCreatedAtDesc(
                            vacancyId, REVERSE_DIRECTION, "PRELIMINARY")) {
                latestByResume.putIfAbsent(run.getResumeId(), run);
            }
            for (Map.Entry<UUID, ScoreRunEntity> entry : latestByResume.entrySet()) {
                if (!seenResumes.add(entry.getKey())) {
                    continue;
                }
                items.add(item(0, null, entry.getKey(), entry.getValue(), null, null));
            }
        }

        Map<UUID, ResumeEntity> resumes = new LinkedHashMap<>();
        if (!seenResumes.isEmpty()) {
            resumeRepository
                    .findAllById(seenResumes)
                    .forEach(resume -> resumes.put(resume.getId(), resume));
        }

        List<RankingItem> withResume =
                items.stream()
                        .map(
                                item -> {
                                    ResumeEntity resume = resumes.get(item.resumeId());
                                    return new RankingItem(
                                            item.rank(),
                                            item.applicationId(),
                                            item.resumeId(),
                                            resume == null ? null : resume.getTitle(),
                                            resume == null ? null : resume.getCreatedAt(),
                                            item.relevance(),
                                            item.score(),
                                            item.scoreLow(),
                                            item.scoreHigh(),
                                            item.capApplied(),
                                            item.decision(),
                                            item.source(),
                                            item.flags());
                                })
                        .sorted(
                                Comparator.comparingDouble(RankingItem::score)
                                        .reversed()
                                        .thenComparing(
                                                item ->
                                                        item.resumeId() == null
                                                                ? ""
                                                                : item.resumeId().toString()))
                        .toList();

        List<RankingItem> ranked = new ArrayList<>(withResume.size());
        for (int index = 0; index < withResume.size(); index++) {
            RankingItem item = withResume.get(index);
            ranked.add(
                    new RankingItem(
                            index + 1,
                            item.applicationId(),
                            item.resumeId(),
                            item.resumeTitle(),
                            item.resumeCreatedAt(),
                            item.relevance(),
                            item.score(),
                            item.scoreLow(),
                            item.scoreHigh(),
                            item.capApplied(),
                            item.decision(),
                            item.source(),
                            item.flags()));
        }
        return new RankingView(vacancyId, normalizedPhase, Instant.now(), ranked);
    }

    private RankingItem item(
            int rank,
            UUID applicationId,
            UUID resumeId,
            ScoreRunEntity run,
            String decision,
            String source) {
        return new RankingItem(
                rank,
                applicationId,
                resumeId,
                null,
                null,
                run.getRelevance() == null ? null : run.getRelevance().doubleValue(),
                run.getScore().doubleValue(),
                run.getScoreLow().doubleValue(),
                run.getScoreHigh().doubleValue(),
                run.isCapApplied(),
                decision,
                source,
                flags(run));
    }

    private String normalizePhase(String phase) {
        if (phase == null || phase.isBlank()) {
            return "FINAL";
        }
        String value = phase.toUpperCase(java.util.Locale.ROOT);
        if (!value.equals("PRELIMINARY") && !value.equals("FINAL")) {
            throw new IllegalArgumentException("phase must be PRELIMINARY or FINAL");
        }
        return value;
    }

    private List<String> flags(ScoreRunEntity run) {
        try {
            return jsonCodec.readList(run.getFlags(), new TypeReference<List<String>>() {});
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    public record RankingView(
            UUID vacancyId, String phase, Instant generatedAt, List<RankingItem> items) {}

    public record RankingItem(
            int rank,
            UUID applicationId,
            UUID resumeId,
            String resumeTitle,
            Instant resumeCreatedAt,
            Double relevance,
            double score,
            double scoreLow,
            double scoreHigh,
            boolean capApplied,
            String decision,
            String source,
            List<String> flags) {}
}

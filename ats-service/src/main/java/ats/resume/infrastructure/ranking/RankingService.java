package ats.resume.infrastructure.ranking;

import ats.resume.infrastructure.json.JsonCodec;
import ats.resume.infrastructure.persistence.entity.ApplicationEntity;
import ats.resume.infrastructure.persistence.entity.ScoreRunEntity;
import ats.resume.infrastructure.persistence.repository.ApplicationRepository;
import ats.resume.infrastructure.persistence.repository.ScoreRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Рейтинг по вакансии. PRELIMINARY и FINAL — разные запуски и разные подписи в UI. */
@Service
@RequiredArgsConstructor
public class RankingService {

    private final ApplicationRepository applicationRepository;
    private final ScoreRunRepository scoreRunRepository;
    private final JsonCodec jsonCodec;

    public RankingView ranking(UUID vacancyId, String phase) {
        String normalizedPhase = normalizePhase(phase);
        List<ApplicationEntity> applications = applicationRepository.findByVacancyId(vacancyId);
        if (applications.isEmpty()) {
            return new RankingView(vacancyId, normalizedPhase, Instant.now(), List.of());
        }
        Map<UUID, ApplicationEntity> byId = new LinkedHashMap<>();
        applications.forEach(application -> byId.put(application.getId(), application));

        List<ScoreRunEntity> runs =
                scoreRunRepository.findByApplicationIdInAndPhaseOrderByCreatedAtDesc(
                        byId.keySet(), normalizedPhase);
        Map<UUID, ScoreRunEntity> latest = new LinkedHashMap<>();
        for (ScoreRunEntity run : runs) {
            latest.putIfAbsent(run.getApplicationId(), run);
        }

        List<RankingItem> items = new ArrayList<>();
        for (Map.Entry<UUID, ScoreRunEntity> entry : latest.entrySet()) {
            ApplicationEntity application = byId.get(entry.getKey());
            ScoreRunEntity run = entry.getValue();
            items.add(
                    new RankingItem(
                            0,
                            application.getId(),
                            run.getRelevance() == null ? null : run.getRelevance().doubleValue(),
                            run.getScore().doubleValue(),
                            run.getScoreLow().doubleValue(),
                            run.getScoreHigh().doubleValue(),
                            run.isCapApplied(),
                            application.getScreeningDecision() == null
                                    ? "NOT_SHORTLISTED"
                                    : application.getScreeningDecision(),
                            flags(run)));
        }
        items.sort(
                Comparator.comparingDouble(RankingItem::score)
                        .reversed()
                        .thenComparing(item -> item.applicationId().toString()));
        List<RankingItem> ranked = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            RankingItem item = items.get(index);
            ranked.add(
                    new RankingItem(
                            index + 1,
                            item.applicationId(),
                            item.relevance(),
                            item.score(),
                            item.scoreLow(),
                            item.scoreHigh(),
                            item.capApplied(),
                            item.decision(),
                            item.flags()));
        }
        return new RankingView(vacancyId, normalizedPhase, Instant.now(), ranked);
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
            Double relevance,
            double score,
            double scoreLow,
            double scoreHigh,
            boolean capApplied,
            String decision,
            List<String> flags) {}
}

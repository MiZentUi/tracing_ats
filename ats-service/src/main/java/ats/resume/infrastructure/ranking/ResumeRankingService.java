package ats.resume.infrastructure.ranking;

import ats.resume.infrastructure.json.JsonCodec;
import ats.resume.infrastructure.persistence.entity.ScoreRunEntity;
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

/** Рейтинг вакансий для резюме. Считается по тем же запускам, что и рейтинг кандидатов. */
@Service
@RequiredArgsConstructor
public class ResumeRankingService {

    private static final String DIRECTION = "RESUME_TO_VACANCIES";

    private final ScoreRunRepository scoreRunRepository;
    private final JsonCodec jsonCodec;

    public RankingView ranking(UUID resumeId, String phase) {
        String normalizedPhase = normalizePhase(phase);
        List<ScoreRunEntity> runs =
                scoreRunRepository.findByResumeIdAndDirectionAndPhaseOrderByCreatedAtDesc(
                        resumeId, DIRECTION, normalizedPhase);
        Map<UUID, ScoreRunEntity> latest = new LinkedHashMap<>();
        for (ScoreRunEntity run : runs) {
            latest.putIfAbsent(run.getVacancyId(), run);
        }

        List<RankingItem> items = new ArrayList<>();
        for (Map.Entry<UUID, ScoreRunEntity> entry : latest.entrySet()) {
            ScoreRunEntity run = entry.getValue();
            items.add(
                    new RankingItem(
                            0,
                            entry.getKey(),
                            run.getVacancyTitle(),
                            run.getRelevance() == null ? null : run.getRelevance().doubleValue(),
                            run.getScore().doubleValue(),
                            run.getScoreLow().doubleValue(),
                            run.getScoreHigh().doubleValue(),
                            flags(run)));
        }
        items.sort(
                Comparator.comparingDouble(RankingItem::score)
                        .reversed()
                        .thenComparing(item -> item.vacancyId().toString()));
        List<RankingItem> ranked = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            RankingItem item = items.get(index);
            ranked.add(
                    new RankingItem(
                            index + 1,
                            item.vacancyId(),
                            item.title(),
                            item.relevance(),
                            item.score(),
                            item.scoreLow(),
                            item.scoreHigh(),
                            item.flags()));
        }
        return new RankingView(resumeId, normalizedPhase, Instant.now(), ranked);
    }

    private String normalizePhase(String phase) {
        if (phase == null || phase.isBlank()) {
            return "PRELIMINARY";
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
            UUID resumeId, String phase, Instant generatedAt, List<RankingItem> items) {}

    public record RankingItem(
            int rank,
            UUID vacancyId,
            String title,
            Double relevance,
            double score,
            double scoreLow,
            double scoreHigh,
            List<String> flags) {}
}

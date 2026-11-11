package ats.resume.infrastructure.screening;

import ats.resume.domain.scoring.ScoringResult;
import ats.resume.domain.vacancy.VacancyTemplate;
import ats.resume.infrastructure.json.JsonCodec;
import ats.resume.infrastructure.persistence.entity.RequirementResultEntity;
import ats.resume.infrastructure.persistence.entity.ScoreRunEntity;
import ats.resume.infrastructure.persistence.repository.RequirementResultRepository;
import ats.resume.infrastructure.persistence.repository.ScoreRunRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Сохраняет неизменяемый запуск скоринга и матрицу требований. */
@Component
@RequiredArgsConstructor
public class ScoreRunWriter {

    private final ScoreRunRepository scoreRunRepository;
    private final RequirementResultRepository requirementResultRepository;
    private final JsonCodec jsonCodec;

    public ScoreRunEntity write(
            UUID applicationId,
            UUID batchId,
            String phase,
            VacancyTemplate template,
            String modelVersion,
            ScoringResult result) {
        ScoreRunEntity run = new ScoreRunEntity();
        run.setApplicationId(applicationId);
        run.setBatchId(batchId);
        run.setPhase(phase);
        run.setTemplateVersion(template.version());
        run.setScoringVersion(template.scoringVersion());
        run.setPromptVersion(template.promptVersion());
        run.setModelVersion(modelVersion);
        run.setWeightsVersion(template.weightsVersion());
        run.setRelevance(decimal(result.relevance(), 3));
        run.setScore(decimal(result.score(), 2));
        run.setScoreLow(decimal(result.scoreLow(), 2));
        run.setScoreHigh(decimal(result.scoreHigh(), 2));
        run.setCapApplied(false);
        run.setFlags(jsonCodec.write(result.flags()));
        run.setParsed("{}");
        run = scoreRunRepository.save(run);

        for (ScoringResult.RequirementResult requirement : result.requirements()) {
            requirementResultRepository.save(requirementResult(run.getId(), requirement));
        }
        return run;
    }

    /**
     * Итоговый запуск не вызывает LLM повторно: копирует уже посчитанные факты предварительного
     * запуска. Внешние доказательства прикрепляются к новой строке отдельно.
     */
    public ScoreRunEntity copy(ScoreRunEntity source, UUID batchId, String phase) {
        ScoreRunEntity run = new ScoreRunEntity();
        run.setApplicationId(source.getApplicationId());
        run.setBatchId(batchId);
        run.setPhase(phase);
        run.setTemplateVersion(source.getTemplateVersion());
        run.setScoringVersion(source.getScoringVersion());
        run.setPromptVersion(source.getPromptVersion());
        run.setModelVersion(source.getModelVersion());
        run.setWeightsVersion(source.getWeightsVersion());
        run.setRelevance(source.getRelevance());
        run.setScore(source.getScore());
        run.setScoreLow(source.getScoreLow());
        run.setScoreHigh(source.getScoreHigh());
        run.setCapApplied(source.isCapApplied());
        run.setFlags(source.getFlags());
        run.setParsed(source.getParsed());
        run = scoreRunRepository.save(run);

        for (RequirementResultEntity sourceRequirement :
                requirementResultRepository.findByScoreRunIdOrderByRequirementId(source.getId())) {
            RequirementResultEntity entity = new RequirementResultEntity();
            entity.setScoreRunId(run.getId());
            entity.setRequirementId(sourceRequirement.getRequirementId());
            entity.setRequirementText(sourceRequirement.getRequirementText());
            entity.setKind(sourceRequirement.getKind());
            entity.setWeight(sourceRequirement.getWeight());
            entity.setCritical(sourceRequirement.isCritical());
            entity.setRelevance(sourceRequirement.getRelevance());
            entity.setCR(sourceRequirement.getCR());
            entity.setTenure(sourceRequirement.getTenure());
            entity.setStatus(sourceRequirement.getStatus());
            entity.setM(sourceRequirement.getM());
            entity.setT(sourceRequirement.getT());
            entity.setRho(sourceRequirement.getRho());
            entity.setEvidenceSource(sourceRequirement.getEvidenceSource());
            entity.setEvidenceQuote(sourceRequirement.getEvidenceQuote());
            entity.setGap(sourceRequirement.getGap());
            entity.setExternal(sourceRequirement.isExternal());
            requirementResultRepository.save(entity);
        }
        return run;
    }

    private RequirementResultEntity requirementResult(
            UUID scoreRunId, ScoringResult.RequirementResult requirement) {
        RequirementResultEntity entity = new RequirementResultEntity();
        entity.setScoreRunId(scoreRunId);
        entity.setRequirementId(requirement.requirementId());
        entity.setRequirementText(requirement.text());
        entity.setKind(requirement.kind().name());
        entity.setWeight(requirement.weight());
        entity.setCritical(requirement.critical());
        entity.setRelevance(decimal(requirement.relevance(), 3));
        entity.setCR(decimal(requirement.coverage(), 3));
        entity.setTenure(requirement.tenure());
        entity.setStatus(requirement.status().name());
        entity.setEvidenceSource(
                requirement.evidenceSource() == null ? null : requirement.evidenceSource().name());
        entity.setEvidenceQuote(requirement.evidenceQuote());
        entity.setGap(requirement.gap());
        entity.setExternal(requirement.external());
        return entity;
    }

    private BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}

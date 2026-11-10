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
        run.setScoringVersion(template.scoringVersion());
        run.setPromptVersion(template.promptVersion());
        run.setModelVersion(modelVersion);
        run.setWeightsVersion(template.weightsVersion());
        run.setScore(decimal(result.score(), 2));
        run.setScoreLow(decimal(result.scoreLow(), 2));
        run.setScoreHigh(decimal(result.scoreHigh(), 2));
        run.setCapApplied(result.capApplied());
        run.setFlags(jsonCodec.write(result.flags()));
        run.setParsed("{}");
        run = scoreRunRepository.save(run);

        for (ScoringResult.RequirementResult requirement : result.requirements()) {
            RequirementResultEntity entity = new RequirementResultEntity();
            entity.setScoreRunId(run.getId());
            entity.setRequirementId(requirement.requirementId());
            entity.setRequirementText(requirement.text());
            entity.setKind(requirement.kind().name());
            entity.setWeight(requirement.weight());
            entity.setCritical(requirement.critical());
            entity.setCR(decimal(requirement.cR(), 3));
            entity.setStatus(requirement.status().name());
            entity.setM(decimal(requirement.m(), 3));
            entity.setT(decimal(requirement.t(), 3));
            entity.setRho(decimal(requirement.rho(), 3));
            entity.setEvidenceSource(
                    requirement.evidenceSource() == null
                            ? null
                            : requirement.evidenceSource().name());
            entity.setEvidenceQuote(requirement.evidenceQuote());
            entity.setGap(requirement.gap());
            entity.setExternal(requirement.external());
            requirementResultRepository.save(entity);
        }
        return run;
    }

    private BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}

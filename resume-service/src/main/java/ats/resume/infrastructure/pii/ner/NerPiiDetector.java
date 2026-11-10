package ats.resume.infrastructure.pii.ner;

import ats.resume.config.PiiProperties;
import ats.resume.domain.privacy.PiiDetector;
import ats.resume.domain.privacy.PiiMaskingException;
import ats.resume.domain.privacy.PiiSpan;
import ats.resume.domain.privacy.PiiType;
import ats.resume.infrastructure.pii.EducationContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Превращает PER/LOC/ORG в PII-спаны. ORG маскируется только в контексте образования: компании
 * остаются в опыте, иначе пострадает домен и объяснимость стажа.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NerPiiDetector implements PiiDetector {

    private final NerModel nerModel;
    private final PiiProperties properties;

    @Override
    public List<PiiSpan> detect(String text) {
        List<NerEntity> entities;
        try {
            entities = mergeAdjacent(nerModel.detect(text));
        } catch (RuntimeException e) {
            if (properties.getNer().isFailClosed()) {
                throw new PiiMaskingException("Local NER is unavailable", e);
            }
            log.warn("Local NER is unavailable, continuing with deterministic detectors only");
            return List.of();
        }

        List<PiiSpan> spans = new ArrayList<>();
        for (NerEntity entity : entities) {
            PiiType type =
                    switch (entity.type()) {
                        case NerEntity.PERSON -> PiiType.NAME;
                        case NerEntity.LOCATION -> PiiType.LOCATION;
                        case NerEntity.ORGANIZATION ->
                                isEducationOrganization(text, entity) ? PiiType.UNIVERSITY : null;
                        default -> null;
                    };
            if (type != null) {
                spans.add(
                        new PiiSpan(
                                entity.start(),
                                entity.end(),
                                type,
                                entity.confidence(),
                                PiiSpan.Source.NER));
            }
        }
        return spans;
    }

    private boolean isEducationOrganization(String text, NerEntity entity) {
        return EducationContext.isUniversityLine(text, entity.start())
                || EducationContext.isInEducationBlock(text, entity.start());
    }

    private List<NerEntity> mergeAdjacent(List<NerEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        List<NerEntity> sorted = new ArrayList<>(entities);
        sorted.sort(Comparator.comparingInt(NerEntity::start).thenComparingInt(NerEntity::end));
        List<NerEntity> merged = new ArrayList<>();
        NerEntity current = sorted.getFirst();
        for (int index = 1; index < sorted.size(); index++) {
            NerEntity next = sorted.get(index);
            if (current.type().equals(next.type())
                    && next.start() <= current.end() + 1
                    && next.start() >= current.start()) {
                current =
                        new NerEntity(
                                current.type(),
                                current.start(),
                                Math.max(current.end(), next.end()),
                                Math.max(current.confidence(), next.confidence()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }
}

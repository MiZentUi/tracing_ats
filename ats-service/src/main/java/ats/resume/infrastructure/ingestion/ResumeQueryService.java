package ats.resume.infrastructure.ingestion;

import ats.resume.infrastructure.json.JsonCodec;
import ats.resume.infrastructure.persistence.entity.ResumeEntity;
import ats.resume.infrastructure.persistence.repository.ResumeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Чтение метаданных резюме без PII. */
@Service
@RequiredArgsConstructor
public class ResumeQueryService {

    private final ResumeRepository resumeRepository;
    private final JsonCodec jsonCodec;

    public Optional<ResumeView> find(UUID resumeId) {
        return resumeRepository
                .findById(resumeId)
                .map(
                        resume ->
                                new ResumeView(
                                        resume.getId(),
                                        resume.getStatus(),
                                        resume.getFileType(),
                                        resume.getOriginalSizeBytes(),
                                        resume.getCreatedAt(),
                                        warnings(resume)));
    }

    private List<String> warnings(ResumeEntity resume) {
        try {
            return jsonCodec.readList(resume.getWarnings(), new TypeReference<List<String>>() {});
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    public record ResumeView(
            UUID resumeId,
            String status,
            String fileType,
            long sizeBytes,
            Instant createdAt,
            List<String> warnings) {}
}

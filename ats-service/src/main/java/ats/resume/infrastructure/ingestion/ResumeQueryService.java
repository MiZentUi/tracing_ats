package ats.resume.infrastructure.ingestion;

import ats.resume.infrastructure.json.JsonCodec;
import ats.resume.infrastructure.persistence.entity.ResumeEntity;
import ats.resume.infrastructure.persistence.repository.ParsedResumeRepository;
import ats.resume.infrastructure.persistence.repository.ResumeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Чтение метаданных резюме без PII. */
@Service
@RequiredArgsConstructor
public class ResumeQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ResumeRepository resumeRepository;
    private final ParsedResumeRepository parsedResumeRepository;
    private final JsonCodec jsonCodec;

    public Optional<ResumeView> find(UUID resumeId) {
        return resumeRepository.findById(resumeId).map(this::view);
    }

    /** Обезличенный текст для визуальной проверки: PII уже заменены плейсхолдерами. */
    public Optional<ResumeTextView> text(UUID resumeId) {
        return resumeRepository
                .findById(resumeId)
                .flatMap(
                        resume ->
                                parsedResumeRepository
                                        .findByResumeId(resumeId)
                                        .map(
                                                parsed ->
                                                        new ResumeTextView(
                                                                resumeId,
                                                                parsed.getMaskedText(),
                                                                warnings(resume))));
    }

    /** История загрузок: новые сначала. `offset` кратен размеру страницы. */
    public ResumePage list(Integer limit, Integer offset) {
        int pageSize = limit == null ? 20 : Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        int pageNumber = offset == null ? 0 : Math.max(0, offset) / pageSize;
        Page<ResumeEntity> page =
                resumeRepository.findAllByOrderByCreatedAtDesc(
                        PageRequest.of(pageNumber, pageSize));
        return new ResumePage(
                page.getContent().stream().map(this::view).toList(), page.getTotalElements());
    }

    private ResumeView view(ResumeEntity resume) {
        return new ResumeView(
                resume.getId(),
                resume.getStatus(),
                resume.getFileType(),
                resume.getOriginalSizeBytes(),
                resume.getCreatedAt(),
                warnings(resume));
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

    public record ResumePage(List<ResumeView> items, long total) {}

    public record ResumeTextView(UUID resumeId, String maskedText, List<String> warnings) {}
}

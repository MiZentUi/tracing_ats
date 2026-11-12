package ats.resume.infrastructure.report;

import ats.resume.domain.privacy.PiiType;
import ats.resume.domain.vault.EncryptedPii;
import ats.resume.domain.vault.PiiPayload;
import ats.resume.domain.vault.PiiVaultCrypto;
import ats.resume.infrastructure.json.JsonCodec;
import ats.resume.infrastructure.persistence.entity.ApplicationEntity;
import ats.resume.infrastructure.persistence.entity.AuditEventEntity;
import ats.resume.infrastructure.persistence.entity.ParsedResumeEntity;
import ats.resume.infrastructure.persistence.entity.PiiVaultEntity;
import ats.resume.infrastructure.persistence.entity.ResumeEntity;
import ats.resume.infrastructure.persistence.repository.ApplicationRepository;
import ats.resume.infrastructure.persistence.repository.AuditEventRepository;
import ats.resume.infrastructure.persistence.repository.ParsedResumeRepository;
import ats.resume.infrastructure.persistence.repository.PiiVaultRepository;
import ats.resume.infrastructure.persistence.repository.ResumeRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Раскрытие PII — отдельное действие с причиной и аудитом. В обычный отчёт PII не попадает. */
@Service
@RequiredArgsConstructor
public class RevealService {

    private final ApplicationRepository applicationRepository;
    private final ResumeRepository resumeRepository;
    private final ParsedResumeRepository parsedResumeRepository;
    private final PiiVaultRepository piiVaultRepository;
    private final PiiVaultCrypto piiVaultCrypto;
    private final AuditEventRepository auditEventRepository;
    private final JsonCodec jsonCodec;

    @Transactional
    public Optional<RevealedCandidate> reveal(UUID applicationId, String reason) {
        Optional<ApplicationEntity> application = applicationRepository.findById(applicationId);
        if (application.isEmpty()) {
            return Optional.empty();
        }
        Optional<ResumeEntity> resume = resumeRepository.findById(application.get().getResumeId());
        if (resume.isEmpty()) {
            return Optional.empty();
        }
        Optional<PiiVaultEntity> vault =
                piiVaultRepository.findByResumeId(application.get().getResumeId());
        if (vault.isEmpty()) {
            return Optional.empty();
        }

        PiiPayload payload =
                piiVaultCrypto.decrypt(
                        application.get().getResumeId(),
                        new EncryptedPii(
                                vault.get().getIv(),
                                vault.get().getCiphertext(),
                                vault.get().getKeyVersion()));

        AuditEventEntity audit = new AuditEventEntity();
        audit.setApplicationId(applicationId);
        audit.setActor("hr");
        audit.setAction("REVEAL_PII");
        audit.setReason(reason);
        audit.setMetadata("{}");
        auditEventRepository.save(audit);

        return Optional.of(
                new RevealedCandidate(
                        value(payload, PiiType.NAME),
                        value(payload, PiiType.EMAIL),
                        value(payload, PiiType.PHONE),
                        payload.university(),
                        payload.graduationYear(),
                        payload.githubUrl(),
                        payload.originalFilename()));
    }

    private String value(PiiPayload payload, PiiType type) {
        return payload.entries().stream()
                .filter(entry -> entry.type() == type)
                .map(PiiPayload.PiiEntry::value)
                .findFirst()
                .orElse(null);
    }

    public record RevealedCandidate(
            String fullName,
            String email,
            String phone,
            String university,
            Integer graduationYear,
            String githubUrl,
            String originalFilename) {}

    /**
     * Возвращает текст резюме до маскировки: подставляет значения PII обратно в плейсхолдеры.
     * Действие аудируется. В обычный отчёт такой текст не попадает.
     */
    @Transactional
    public Optional<String> revealText(UUID resumeId, UUID applicationId, String reason) {
        Optional<ParsedResumeEntity> parsed = parsedResumeRepository.findByResumeId(resumeId);
        Optional<PiiVaultEntity> vault = piiVaultRepository.findByResumeId(resumeId);
        if (parsed.isEmpty() || vault.isEmpty()) {
            return Optional.empty();
        }

        PiiPayload payload =
                piiVaultCrypto.decrypt(
                        resumeId,
                        new EncryptedPii(
                                vault.get().getIv(),
                                vault.get().getCiphertext(),
                                vault.get().getKeyVersion()));

        String text = parsed.get().getMaskedText();
        for (PiiPayload.PiiEntry entry : payload.entries()) {
            text = text.replace(entry.placeholder(), entry.value());
        }

        AuditEventEntity audit = new AuditEventEntity();
        audit.setApplicationId(applicationId);
        audit.setActor("hr");
        audit.setAction("REVEAL_RESUME_TEXT");
        audit.setReason(reason);
        audit.setMetadata(jsonCodec.write(Map.of("resumeId", resumeId.toString())));
        auditEventRepository.save(audit);

        return Optional.of(text);
    }
}

package ats.resume.infrastructure.report;

import ats.resume.domain.privacy.PiiType;
import ats.resume.domain.vault.EncryptedPii;
import ats.resume.domain.vault.PiiPayload;
import ats.resume.domain.vault.PiiVaultCrypto;
import ats.resume.infrastructure.persistence.entity.ApplicationEntity;
import ats.resume.infrastructure.persistence.entity.AuditEventEntity;
import ats.resume.infrastructure.persistence.entity.PiiVaultEntity;
import ats.resume.infrastructure.persistence.entity.ResumeEntity;
import ats.resume.infrastructure.persistence.repository.ApplicationRepository;
import ats.resume.infrastructure.persistence.repository.AuditEventRepository;
import ats.resume.infrastructure.persistence.repository.PiiVaultRepository;
import ats.resume.infrastructure.persistence.repository.ResumeRepository;
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
    private final PiiVaultRepository piiVaultRepository;
    private final PiiVaultCrypto piiVaultCrypto;
    private final AuditEventRepository auditEventRepository;

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
}

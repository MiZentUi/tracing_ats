package ats.resume.infrastructure.edit;

import ats.gateway.model.UpdateResumeRequest;
import ats.resume.domain.ingestion.ResumeMaskedEvent;
import ats.resume.domain.ingestion.ResumeMatchRequestedEvent;
import ats.resume.domain.privacy.MaskingResult;
import ats.resume.domain.privacy.SanitizedText;
import ats.resume.domain.vault.EncryptedPii;
import ats.resume.domain.vault.PiiVaultCrypto;
import ats.resume.infrastructure.ingestion.ResumeQueryService;
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
import ats.resume.infrastructure.persistence.repository.ResumeExtractionRepository;
import ats.resume.infrastructure.persistence.repository.ResumeRepository;
import ats.resume.infrastructure.pii.PiiMaskingService;
import ats.resume.infrastructure.pii.PromptInjectionSanitizer;
import ats.resume.infrastructure.pii.ner.NerModel;
import ats.resume.infrastructure.screening.ResumeMatchService;
import ats.vacancy.application.ApiException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Редактирование резюме. Текст приходит с PII после отдельного reveal, поэтому сервис заново
 * маскирует его, заменяет vault и parsed_resume и запускает пересчёт.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeEditService {

    private final ResumeRepository resumeRepository;
    private final ParsedResumeRepository parsedResumeRepository;
    private final PiiVaultRepository piiVaultRepository;
    private final ResumeExtractionRepository extractionRepository;
    private final ApplicationRepository applicationRepository;
    private final AuditEventRepository auditEventRepository;
    private final PiiMaskingService piiMaskingService;
    private final PromptInjectionSanitizer promptInjectionSanitizer;
    private final PiiVaultCrypto vaultCrypto;
    private final NerModel nerModel;
    private final JsonCodec jsonCodec;
    private final ResumeMatchService resumeMatchService;
    private final ApplicationEventPublisher eventPublisher;
    private final ResumeQueryService queryService;

    @Transactional
    public ResumeQueryService.ResumeView update(UUID resumeId, UpdateResumeRequest request) {
        ResumeEntity resume =
                resumeRepository
                        .findById(resumeId)
                        .orElseThrow(() -> ApiException.notFound("Resume not found"));

        boolean textChanged = request.getText() != null;
        if (request.getTitle() != null) {
            String title = request.getTitle().strip();
            resume.setTitle(title.isEmpty() ? null : title);
        }

        if (textChanged) {
            String text = request.getText().strip();
            if (text.isEmpty()) {
                throw ApiException.unprocessable("TEXT_REQUIRED", "Resume text must not be empty");
            }
            String reason = request.getReason() == null ? "" : request.getReason().strip();
            if (reason.length() < 3) {
                throw ApiException.unprocessable(
                        "REASON_REQUIRED", "Reason is required when resume text changes");
            }
            replaceContent(resume, text, reason);
        }

        resumeRepository.save(resume);
        log.info(
                "resume_updated resume_id={} title_changed={} text_changed={}",
                resumeId,
                request.getTitle() != null,
                textChanged);
        return queryService
                .find(resumeId)
                .orElseThrow(() -> ApiException.notFound("Resume not found"));
    }

    private void replaceContent(ResumeEntity resume, String text, String reason) {
        UUID resumeId = resume.getId();
        MaskingResult masking = piiMaskingService.mask(text, null, null);
        SanitizedText sanitized = promptInjectionSanitizer.sanitize(masking.maskedText());
        EncryptedPii encrypted = vaultCrypto.encrypt(resumeId, masking.payload());

        parsedResumeRepository.deleteByResumeId(resumeId);
        piiVaultRepository.deleteByResumeId(resumeId);
        extractionRepository.deleteByResumeId(resumeId);

        ParsedResumeEntity parsed = new ParsedResumeEntity();
        parsed.setResumeId(resumeId);
        parsed.setMaskedText(sanitized.text());
        parsed.setInjectionFlags(jsonCodec.write(sanitized.flags()));
        parsed.setEducationLevel(masking.education().level());
        parsed.setDegree(masking.education().degree());
        parsed.setSpecialty(masking.education().specialty());
        parsed.setEducationYears(
                masking.education().years() == null
                        ? null
                        : BigDecimal.valueOf(masking.education().years()));
        parsed.setPiiModel(nerModel.modelName());
        parsed.setPiiModelHash(nerModel.modelHash());
        parsed.setNerUsed(nerModelUsed());
        parsedResumeRepository.save(parsed);

        PiiVaultEntity vault = new PiiVaultEntity();
        vault.setResumeId(resumeId);
        vault.setKeyVersion(encrypted.keyVersion());
        vault.setIv(encrypted.iv());
        vault.setCiphertext(encrypted.ciphertext());
        piiVaultRepository.save(vault);

        resume.setStatus("MASKED");
        resume.setWarnings("[]");

        List<ApplicationEntity> applications = applicationRepository.findByResumeId(resumeId);
        for (ApplicationEntity application : applications) {
            application.setStatus("MASKED");
            application.setScreeningDecision(null);
            application.setPreliminaryScoreRunId(null);
            application.setFinalScoreRunId(null);
            applicationRepository.save(application);
        }

        if (applications.isEmpty()) {
            UUID batchId = resumeMatchService.createBatch(resumeId);
            eventPublisher.publishEvent(new ResumeMatchRequestedEvent(batchId, resumeId));
        } else {
            applications.forEach(
                    application ->
                            eventPublisher.publishEvent(
                                    new ResumeMaskedEvent(application.getId(), resumeId)));
        }

        AuditEventEntity audit = new AuditEventEntity();
        audit.setActor("hr");
        audit.setAction("EDIT_RESUME_TEXT");
        audit.setReason(reason);
        audit.setMetadata(jsonCodec.write(Map.of("resumeId", resumeId.toString())));
        auditEventRepository.save(audit);
    }

    private boolean nerModelUsed() {
        return !"fake-ner".equals(nerModel.modelName()) && !"fake".equals(nerModel.modelHash());
    }
}

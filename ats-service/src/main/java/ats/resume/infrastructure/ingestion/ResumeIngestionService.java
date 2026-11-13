package ats.resume.infrastructure.ingestion;

import ats.extraction.domain.ExtractedText;
import ats.extraction.domain.FileType;
import ats.extraction.domain.FileTypeDetector;
import ats.extraction.domain.TextExtractionService;
import ats.extraction.domain.UploadedFile;
import ats.extraction.domain.error.ExtractionException;
import ats.resume.domain.ingestion.ResumeMaskedEvent;
import ats.resume.domain.ingestion.ResumeMatchRequestedEvent;
import ats.resume.domain.ingestion.UploadOutcome;
import ats.resume.domain.privacy.MaskingResult;
import ats.resume.domain.privacy.SanitizedText;
import ats.resume.domain.storage.ObjectStorage;
import ats.resume.domain.vault.EncryptedPii;
import ats.resume.domain.vault.PiiVaultCrypto;
import ats.resume.infrastructure.json.JsonCodec;
import ats.resume.infrastructure.persistence.entity.ApplicationEntity;
import ats.resume.infrastructure.persistence.entity.CandidateEntity;
import ats.resume.infrastructure.persistence.entity.ParsedResumeEntity;
import ats.resume.infrastructure.persistence.entity.PiiVaultEntity;
import ats.resume.infrastructure.persistence.entity.ResumeEntity;
import ats.resume.infrastructure.persistence.repository.ApplicationRepository;
import ats.resume.infrastructure.persistence.repository.CandidateRepository;
import ats.resume.infrastructure.persistence.repository.ParsedResumeRepository;
import ats.resume.infrastructure.persistence.repository.PiiVaultRepository;
import ats.resume.infrastructure.persistence.repository.ResumeRepository;
import ats.resume.infrastructure.pii.PiiMaskingService;
import ats.resume.infrastructure.pii.PromptInjectionSanitizer;
import ats.resume.infrastructure.pii.ner.NerModel;
import ats.resume.infrastructure.screening.ResumeMatchService;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Загрузка резюме: оригинал в объектное хранилище, PII в шифрованный vault, обезличенный текст в
 * parsed_resume. Ни имя файла, ни текст резюме не попадают в логи. Внешний вход (hh) использует тот
 * же путь, но получает текст из коннектора и помечает источник.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeIngestionService {

    private final TextExtractionService textExtractionService;
    private final FileTypeDetector fileTypeDetector;
    private final PiiMaskingService piiMaskingService;
    private final PromptInjectionSanitizer promptInjectionSanitizer;
    private final PiiVaultCrypto piiVaultCrypto;
    private final ObjectStorage objectStorage;
    private final JsonCodec jsonCodec;
    private final NerModel nerModel;
    private final CandidateRepository candidateRepository;
    private final ResumeRepository resumeRepository;
    private final PiiVaultRepository piiVaultRepository;
    private final ParsedResumeRepository parsedResumeRepository;
    private final ApplicationRepository applicationRepository;
    private final ResumeMatchService resumeMatchService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UploadOutcome ingest(UUID vacancyId, boolean consentGithub, MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            return new UploadOutcome(
                    null, UploadOutcome.Status.FAILED, List.of("FILE_READ_FAILED"));
        }
        return ingestContent(
                vacancyId,
                consentGithub,
                content,
                file.getOriginalFilename(),
                "MANUAL",
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * Резюме из hh.ru. Текст уже приведён коннектором к обычному виду; дальше работает общий
     * пайплайн маскировки и скоринга.
     */
    @Transactional
    public UploadOutcome ingestExternal(
            UUID vacancyId,
            String applicationExternalRef,
            String candidateExternalRef,
            String resumeExternalRef,
            String title,
            String text) {
        return ingestContent(
                vacancyId,
                false,
                text.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "hh-resume.txt",
                "HH",
                candidateExternalRef,
                resumeExternalRef,
                applicationExternalRef,
                title,
                FileType.TEXT);
    }

    private UploadOutcome ingestContent(
            UUID vacancyId,
            boolean consentGithub,
            byte[] content,
            String originalFilename,
            String source,
            String candidateExternalRef,
            String resumeExternalRef,
            String applicationExternalRef,
            String title,
            FileType forcedType) {
        CandidateEntity candidate = findOrCreateCandidate(source, candidateExternalRef);
        UUID candidateId = candidate.getId();

        FileType fileType = forcedType == null ? detectType(originalFilename, content) : forcedType;
        ResumeEntity resume =
                newResume(candidateId, fileType, content, source, resumeExternalRef, title);
        resume = resumeRepository.save(resume);
        UUID resumeId = resume.getId();
        objectStorage.put(resume.getOriginalObjectKey(), content, contentType(fileType));

        ExtractedText extracted;
        try {
            extracted =
                    textExtractionService.extract(
                            new UploadedFile(
                                    originalFilename == null ? "resume" : originalFilename,
                                    content));
        } catch (ExtractionException e) {
            String status =
                    e.errorCode() == ats.extraction.domain.error.ErrorCode.TEXT_EMPTY
                            ? UploadOutcome.Status.TEXT_EMPTY.name()
                            : UploadOutcome.Status.FAILED.name();
            resume.setStatus(status);
            resume.setWarnings(jsonCodec.write(List.of(e.errorCode().name())));
            resumeRepository.save(resume);
            ApplicationEntity application =
                    createApplication(
                            vacancyId,
                            resumeId,
                            candidateId,
                            consentGithub,
                            status,
                            source,
                            applicationExternalRef);
            log.info(
                    "resume_ingested resume_id={} application_id={} status={} file_type={}"
                            + " size_bytes={} warnings={}",
                    resumeId,
                    application == null ? null : application.getId(),
                    status,
                    fileType,
                    content.length,
                    e.errorCode().name());
            return new UploadOutcome(
                    resumeId,
                    UploadOutcome.Status.valueOf(status),
                    List.of(e.errorCode().name()),
                    null,
                    application == null ? null : application.getId());
        }

        MaskingResult masking;
        try {
            masking = piiMaskingService.mask(extracted.text(), originalFilename, null);
        } catch (RuntimeException e) {
            resume.setStatus(UploadOutcome.Status.MASKING_FAILED.name());
            resume.setWarnings(jsonCodec.write(List.of("PII_MASKING_FAILED")));
            resumeRepository.save(resume);
            ApplicationEntity application =
                    createApplication(
                            vacancyId,
                            resumeId,
                            candidateId,
                            consentGithub,
                            UploadOutcome.Status.MASKING_FAILED.name(),
                            source,
                            applicationExternalRef);
            log.warn("pii_masking_failed resume_id={}", resumeId);
            log.info(
                    "resume_ingested resume_id={} application_id={} status=MASKING_FAILED"
                            + " file_type={} size_bytes={}",
                    resumeId,
                    application == null ? null : application.getId(),
                    fileType,
                    content.length);
            return new UploadOutcome(
                    resumeId,
                    UploadOutcome.Status.MASKING_FAILED,
                    List.of("PII_MASKING_FAILED"),
                    null,
                    application == null ? null : application.getId());
        }

        SanitizedText sanitized = promptInjectionSanitizer.sanitize(masking.maskedText());
        EncryptedPii encrypted = piiVaultCrypto.encrypt(resumeId, masking.payload());

        PiiVaultEntity vault = new PiiVaultEntity();
        vault.setResumeId(resumeId);
        vault.setKeyVersion(encrypted.keyVersion());
        vault.setIv(encrypted.iv());
        vault.setCiphertext(encrypted.ciphertext());
        piiVaultRepository.save(vault);

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

        resume.setStatus(UploadOutcome.Status.MASKED.name());
        resume.setWarnings(jsonCodec.write(extracted.warnings().stream().map(Enum::name).toList()));
        resumeRepository.save(resume);
        ApplicationEntity application =
                createApplication(
                        vacancyId,
                        resumeId,
                        candidateId,
                        consentGithub,
                        UploadOutcome.Status.MASKED.name(),
                        source,
                        applicationExternalRef);
        UUID batchId = null;
        if (application != null) {
            eventPublisher.publishEvent(new ResumeMaskedEvent(application.getId(), resumeId));
        } else {
            // Режим «вакансии под резюме»: отклика нет, сравниваем со всеми вакансиями.
            batchId = resumeMatchService.createBatch(resumeId);
            eventPublisher.publishEvent(new ResumeMatchRequestedEvent(batchId, resumeId));
        }
        log.info(
                "resume_ingested resume_id={} application_id={} batch_id={} status=MASKED"
                        + " source={} file_type={} size_bytes={} text_chars={} warnings={}",
                resumeId,
                application == null ? null : application.getId(),
                batchId,
                source,
                fileType,
                content.length,
                extracted.text().length(),
                extracted.warnings().size());

        return new UploadOutcome(
                resumeId,
                UploadOutcome.Status.MASKED,
                extracted.warnings().stream().map(Enum::name).toList(),
                batchId,
                application == null ? null : application.getId());
    }

    private CandidateEntity findOrCreateCandidate(String source, String externalRef) {
        if (!"MANUAL".equals(source) && externalRef != null && !externalRef.isBlank()) {
            return candidateRepository
                    .findBySourceAndExternalRef(source, externalRef)
                    .orElseGet(
                            () -> {
                                CandidateEntity candidate = new CandidateEntity();
                                candidate.setSource(source);
                                candidate.setExternalRef(externalRef);
                                return candidateRepository.save(candidate);
                            });
        }
        CandidateEntity candidate = new CandidateEntity();
        candidate.setSource(source);
        candidate.setExternalRef(externalRef);
        return candidateRepository.save(candidate);
    }

    private ResumeEntity newResume(
            UUID candidateId,
            FileType fileType,
            byte[] content,
            String source,
            String externalRef,
            String title) {
        ResumeEntity resume = new ResumeEntity();
        resume.setCandidateId(candidateId);
        resume.setOriginalObjectKey("resumes/" + UUID.randomUUID());
        resume.setOriginalSha256(sha256(content));
        resume.setOriginalSizeBytes(content.length);
        resume.setFileType(fileType.name());
        resume.setSource(source);
        resume.setExternalRef(externalRef);
        resume.setTitle(title);
        resume.setStatus("UPLOADED");
        resume.setWarnings("[]");
        return resume;
    }

    private ApplicationEntity createApplication(
            UUID vacancyId,
            UUID resumeId,
            UUID candidateId,
            boolean consentGithub,
            String status,
            String source,
            String externalRef) {
        if (vacancyId == null) {
            return null;
        }
        ApplicationEntity application = new ApplicationEntity();
        application.setVacancyId(vacancyId);
        application.setResumeId(resumeId);
        application.setCandidateId(candidateId);
        application.setConsentGithub(consentGithub);
        application.setStatus(status);
        application.setSource(source);
        application.setExternalRef(externalRef);
        return applicationRepository.save(application);
    }

    private FileType detectType(String fileName, byte[] content) {
        try {
            return fileTypeDetector.detect(fileName == null ? "resume" : fileName, content);
        } catch (RuntimeException e) {
            return FileType.TEXT;
        }
    }

    private String contentType(FileType fileType) {
        return switch (fileType) {
            case PDF -> "application/pdf";
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case MARKDOWN -> "text/markdown";
            case TEXT -> "text/plain";
        };
    }

    private boolean nerModelUsed() {
        return !"fake-ner".equals(nerModel.modelName()) && !"fake".equals(nerModel.modelHash());
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

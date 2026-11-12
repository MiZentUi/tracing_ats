package ats.resume.infrastructure.ingestion;

import ats.extraction.domain.ExtractedText;
import ats.extraction.domain.FileType;
import ats.extraction.domain.FileTypeDetector;
import ats.extraction.domain.TextExtractionService;
import ats.extraction.domain.UploadedFile;
import ats.extraction.domain.error.ExtractionException;
import ats.resume.domain.ingestion.ResumeMaskedEvent;
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
 * parsed_resume. Ни имя файла, ни текст резюме не попадают в логи.
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
        CandidateEntity candidate = candidateRepository.save(new CandidateEntity());
        UUID candidateId = candidate.getId();

        String originalFilename = file.getOriginalFilename();
        FileType fileType = detectType(originalFilename, content);
        ResumeEntity resume = newResume(candidateId, fileType, content);
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
            createApplication(vacancyId, resumeId, candidateId, consentGithub, status);
            log.info(
                    "resume_ingested resume_id={} status={} file_type={} size_bytes={}"
                            + " warnings={}",
                    resumeId,
                    status,
                    fileType,
                    content.length,
                    e.errorCode().name());
            return new UploadOutcome(
                    resumeId, UploadOutcome.Status.valueOf(status), List.of(e.errorCode().name()));
        }

        MaskingResult masking;
        try {
            masking = piiMaskingService.mask(extracted.text(), originalFilename, null);
        } catch (RuntimeException e) {
            resume.setStatus(UploadOutcome.Status.MASKING_FAILED.name());
            resume.setWarnings(jsonCodec.write(List.of("PII_MASKING_FAILED")));
            resumeRepository.save(resume);
            createApplication(
                    vacancyId,
                    resumeId,
                    candidateId,
                    consentGithub,
                    UploadOutcome.Status.MASKING_FAILED.name());
            log.warn("pii_masking_failed resume_id={}", resumeId);
            log.info(
                    "resume_ingested resume_id={} status=MASKING_FAILED file_type={} size_bytes={}",
                    resumeId,
                    fileType,
                    content.length);
            return new UploadOutcome(
                    resumeId, UploadOutcome.Status.MASKING_FAILED, List.of("PII_MASKING_FAILED"));
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
                        UploadOutcome.Status.MASKED.name());
        eventPublisher.publishEvent(new ResumeMaskedEvent(application.getId()));
        log.info(
                "resume_ingested resume_id={} application_id={} status=MASKED file_type={}"
                        + " size_bytes={} text_chars={} warnings={}",
                resumeId,
                application.getId(),
                fileType,
                content.length,
                extracted.text().length(),
                extracted.warnings().size());

        return new UploadOutcome(
                resumeId,
                UploadOutcome.Status.MASKED,
                extracted.warnings().stream().map(Enum::name).toList());
    }

    private ResumeEntity newResume(UUID candidateId, FileType fileType, byte[] content) {
        ResumeEntity resume = new ResumeEntity();
        resume.setCandidateId(candidateId);
        resume.setOriginalObjectKey("resumes/" + UUID.randomUUID());
        resume.setOriginalSha256(sha256(content));
        resume.setOriginalSizeBytes(content.length);
        resume.setFileType(fileType.name());
        resume.setStatus("UPLOADED");
        resume.setWarnings("[]");
        return resume;
    }

    private ApplicationEntity createApplication(
            UUID vacancyId, UUID resumeId, UUID candidateId, boolean consentGithub, String status) {
        ApplicationEntity application = new ApplicationEntity();
        application.setVacancyId(vacancyId);
        application.setResumeId(resumeId);
        application.setCandidateId(candidateId);
        application.setConsentGithub(consentGithub);
        application.setStatus(status);
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

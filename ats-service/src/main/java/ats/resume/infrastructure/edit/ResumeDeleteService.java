package ats.resume.infrastructure.edit;

import ats.resume.domain.storage.ObjectStorage;
import ats.resume.infrastructure.json.JsonCodec;
import ats.resume.infrastructure.persistence.entity.AuditEventEntity;
import ats.resume.infrastructure.persistence.entity.ResumeEntity;
import ats.resume.infrastructure.persistence.repository.AuditEventRepository;
import ats.resume.infrastructure.persistence.repository.ResumeRepository;
import ats.vacancy.application.ApiException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Удаление резюме и связанного оригинала. PII и его ключи в логи не попадают. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeDeleteService {

    private final ResumeRepository resumeRepository;
    private final ObjectStorage objectStorage;
    private final AuditEventRepository auditEventRepository;
    private final JsonCodec jsonCodec;

    @Transactional
    public void delete(UUID resumeId) {
        ResumeEntity resume =
                resumeRepository
                        .findById(resumeId)
                        .orElseThrow(() -> ApiException.notFound("Resume not found"));
        try {
            objectStorage.delete(resume.getOriginalObjectKey());
        } catch (RuntimeException e) {
            // БД всё равно очищаем: иначе резюме с PII останется доступным.
            log.warn(
                    "resume_original_delete_failed resume_id={} error={}",
                    resumeId,
                    e.getClass().getSimpleName());
        }
        resumeRepository.delete(resume);

        AuditEventEntity audit = new AuditEventEntity();
        audit.setActor("hr");
        audit.setAction("DELETE_RESUME");
        audit.setReason("resume deleted");
        audit.setMetadata(jsonCodec.write(Map.of("resumeId", resumeId.toString())));
        auditEventRepository.save(audit);

        log.info("resume_deleted resume_id={}", resumeId);
    }
}

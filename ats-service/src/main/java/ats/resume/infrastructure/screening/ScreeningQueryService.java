package ats.resume.infrastructure.screening;

import ats.resume.infrastructure.persistence.entity.ScreeningBatchEntity;
import ats.resume.infrastructure.persistence.repository.ScreeningBatchRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Чтение статуса пакета скрининга. */
@Service
@RequiredArgsConstructor
public class ScreeningQueryService {

    private final ScreeningBatchRepository batchRepository;

    public Optional<ScreeningBatchEntity> find(UUID batchId) {
        return batchRepository.findById(batchId);
    }
}

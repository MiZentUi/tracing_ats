package ats.vacancy.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LevelRepository extends JpaRepository<LevelEntity, UUID> {

    Optional<LevelEntity> findByCodeAndActiveTrue(String code);

    List<LevelEntity> findAllByActiveTrueOrderBySortOrder();
}

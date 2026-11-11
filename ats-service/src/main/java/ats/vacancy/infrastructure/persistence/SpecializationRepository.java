package ats.vacancy.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecializationRepository extends JpaRepository<SpecializationEntity, UUID> {

    Optional<SpecializationEntity> findByCodeAndActiveTrue(String code);

    Optional<SpecializationEntity> findByCode(String code);

    Optional<SpecializationEntity> findByNameIgnoreCase(String name);

    List<SpecializationEntity> findAllByActiveTrueOrderByName();
}

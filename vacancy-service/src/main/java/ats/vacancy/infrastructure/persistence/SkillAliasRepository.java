package ats.vacancy.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SkillAliasRepository extends JpaRepository<SkillAliasEntity, UUID> {

    @Query(
            "select a.aliasNormalized, s.id, s.name from SkillAliasEntity a, SkillEntity s "
                    + "where s.id = a.skillId")
    List<Object[]> findAllAliases();
}

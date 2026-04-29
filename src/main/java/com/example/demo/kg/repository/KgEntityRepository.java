package com.example.demo.kg.repository;

import com.example.demo.kg.domain.KgEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface KgEntityRepository extends JpaRepository<KgEntity, UUID> {
    List<KgEntity> findByEntityType(String entityType);
    List<KgEntity> findByGraphVersionId(UUID graphVersionId);
}

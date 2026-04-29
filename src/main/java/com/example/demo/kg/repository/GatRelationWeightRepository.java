package com.example.demo.kg.repository;

import com.example.demo.kg.domain.GatRelationWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface GatRelationWeightRepository extends JpaRepository<GatRelationWeight, UUID> {
    List<GatRelationWeight> findByGatTaskId(UUID gatTaskId);
}

package com.example.demo.qc.repository;

import com.example.demo.qc.domain.QualityMetricDef;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QualityMetricDefRepository extends JpaRepository<QualityMetricDef, UUID> {
    Optional<QualityMetricDef> findByStepIdAndMetricCode(UUID stepId, String metricCode);
    List<QualityMetricDef> findByStepId(UUID stepId);
}

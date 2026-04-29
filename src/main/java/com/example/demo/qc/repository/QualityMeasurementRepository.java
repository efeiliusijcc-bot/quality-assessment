package com.example.demo.qc.repository;

import com.example.demo.qc.domain.QualityMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface QualityMeasurementRepository extends JpaRepository<QualityMeasurement, UUID> {
    List<QualityMeasurement> findByRunIdOrderByMeasuredAtAsc(UUID runId);
}

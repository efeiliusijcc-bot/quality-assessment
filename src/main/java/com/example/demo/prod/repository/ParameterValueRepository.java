package com.example.demo.prod.repository;

import com.example.demo.prod.domain.ParameterValue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ParameterValueRepository extends JpaRepository<ParameterValue, UUID> {
    List<ParameterValue> findByRunIdOrderByMeasuredAtAsc(UUID runId);
    List<ParameterValue> findByRunIdAndParamId(UUID runId, UUID paramId);
}

package com.example.demo.core.repository;

import com.example.demo.core.domain.ParameterDef;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParameterDefRepository extends JpaRepository<ParameterDef, UUID> {
    Optional<ParameterDef> findByStepIdAndParamCodeAndParamCategory(UUID stepId, String paramCode, String paramCategory);
    List<ParameterDef> findByStepId(UUID stepId);
}

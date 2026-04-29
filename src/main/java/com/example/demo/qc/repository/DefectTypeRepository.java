package com.example.demo.qc.repository;

import com.example.demo.qc.domain.DefectType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DefectTypeRepository extends JpaRepository<DefectType, UUID> {
    Optional<DefectType> findByStepIdAndDefectCode(UUID stepId, String defectCode);
    List<DefectType> findByStepId(UUID stepId);
}

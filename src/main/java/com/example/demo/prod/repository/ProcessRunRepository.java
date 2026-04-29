package com.example.demo.prod.repository;

import com.example.demo.prod.domain.ProcessRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProcessRunRepository extends JpaRepository<ProcessRun, UUID> {
    List<ProcessRun> findByBatchIdOrderByCreatedAtAsc(UUID batchId);
    List<ProcessRun> findByBatchIdAndStepId(UUID batchId, UUID stepId);
    List<ProcessRun> findByUnitIdOrderByCreatedAtAsc(UUID unitId);
}

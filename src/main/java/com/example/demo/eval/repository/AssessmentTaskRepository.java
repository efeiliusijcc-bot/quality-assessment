package com.example.demo.eval.repository;

import com.example.demo.eval.domain.AssessmentTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AssessmentTaskRepository extends JpaRepository<AssessmentTask, UUID> {
    List<AssessmentTask> findByBatchIdOrderByCreatedAtDesc(UUID batchId);
    List<AssessmentTask> findByBatchIdAndTaskType(UUID batchId, String taskType);
}

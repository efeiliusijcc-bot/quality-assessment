package com.example.demo.eval.repository;

import com.example.demo.eval.domain.OptimizationTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OptimizationTaskRepository extends JpaRepository<OptimizationTask, UUID> {
    List<OptimizationTask> findByBatchIdOrderByCreatedAtDesc(UUID batchId);
}

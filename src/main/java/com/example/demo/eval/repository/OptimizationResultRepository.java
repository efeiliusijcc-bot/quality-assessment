package com.example.demo.eval.repository;

import com.example.demo.eval.domain.OptimizationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OptimizationResultRepository extends JpaRepository<OptimizationResult, UUID> {
    List<OptimizationResult> findByOptTaskIdOrderByParetoRankAsc(UUID optTaskId);
}

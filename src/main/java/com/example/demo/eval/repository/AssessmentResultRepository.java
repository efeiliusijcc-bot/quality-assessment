package com.example.demo.eval.repository;

import com.example.demo.eval.domain.AssessmentResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AssessmentResultRepository extends JpaRepository<AssessmentResult, UUID> {
    List<AssessmentResult> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
}

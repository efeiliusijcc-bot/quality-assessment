package com.example.demo.core.repository;

import com.example.demo.core.domain.ProcessStep;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ProcessStepRepository extends JpaRepository<ProcessStep, UUID> {
    Optional<ProcessStep> findByStepCode(String stepCode);
}

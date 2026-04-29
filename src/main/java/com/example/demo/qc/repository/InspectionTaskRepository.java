package com.example.demo.qc.repository;

import com.example.demo.qc.domain.InspectionTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface InspectionTaskRepository extends JpaRepository<InspectionTask, UUID> {
    List<InspectionTask> findByRunIdOrderByInspectedAtDesc(UUID runId);
}

package com.example.demo.etl.repository;

import com.example.demo.etl.domain.CleaningLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CleaningLogRepository extends JpaRepository<CleaningLog, UUID> {
    List<CleaningLog> findByRuleId(UUID ruleId);
    List<CleaningLog> findBySourceTable(String sourceTable);
}

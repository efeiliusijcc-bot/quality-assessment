package com.example.demo.etl.repository;

import com.example.demo.etl.domain.CleaningRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CleaningRuleRepository extends JpaRepository<CleaningRule, UUID> {
    List<CleaningRule> findByEnabledFlagTrueOrderByPriorityNoAsc();
    List<CleaningRule> findByTargetCategory(String targetCategory);
}

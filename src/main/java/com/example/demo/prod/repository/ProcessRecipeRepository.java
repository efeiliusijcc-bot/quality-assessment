package com.example.demo.prod.repository;

import com.example.demo.prod.domain.ProcessRecipe;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProcessRecipeRepository extends JpaRepository<ProcessRecipe, UUID> {
    List<ProcessRecipe> findByStepIdAndIsActiveTrue(UUID stepId);
}

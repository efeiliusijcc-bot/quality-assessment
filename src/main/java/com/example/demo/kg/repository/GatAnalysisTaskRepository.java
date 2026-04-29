package com.example.demo.kg.repository;

import com.example.demo.kg.domain.GatAnalysisTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface GatAnalysisTaskRepository extends JpaRepository<GatAnalysisTask, UUID> {}

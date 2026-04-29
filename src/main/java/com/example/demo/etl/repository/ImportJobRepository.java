package com.example.demo.etl.repository;

import com.example.demo.etl.domain.ImportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {
    List<ImportJob> findByImportStatusOrderByStartedAtDesc(String importStatus);
}

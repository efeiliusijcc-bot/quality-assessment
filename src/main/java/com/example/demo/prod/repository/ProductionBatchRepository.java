package com.example.demo.prod.repository;

import com.example.demo.prod.domain.ProductionBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ProductionBatchRepository extends JpaRepository<ProductionBatch, UUID> {
    Optional<ProductionBatch> findByBatchNo(String batchNo);
}

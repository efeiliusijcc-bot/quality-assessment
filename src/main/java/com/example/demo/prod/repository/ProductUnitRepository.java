package com.example.demo.prod.repository;

import com.example.demo.prod.domain.ProductUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProductUnitRepository extends JpaRepository<ProductUnit, UUID> {
    List<ProductUnit> findByBatchId(UUID batchId);
}

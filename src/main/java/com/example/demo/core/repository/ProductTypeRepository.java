package com.example.demo.core.repository;

import com.example.demo.core.domain.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ProductTypeRepository extends JpaRepository<ProductType, UUID> {
    Optional<ProductType> findByProductCode(String productCode);
}

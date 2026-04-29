package com.example.demo.core.repository;

import com.example.demo.core.domain.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EquipmentRepository extends JpaRepository<Equipment, UUID> {
    Optional<Equipment> findByEquipmentCode(String equipmentCode);
}

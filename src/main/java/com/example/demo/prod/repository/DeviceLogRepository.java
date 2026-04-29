package com.example.demo.prod.repository;

import com.example.demo.prod.domain.DeviceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DeviceLogRepository extends JpaRepository<DeviceLog, UUID> {
    List<DeviceLog> findByEquipmentIdOrderByLogTimeDesc(UUID equipmentId);
}

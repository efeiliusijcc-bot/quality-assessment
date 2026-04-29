package com.example.demo.qc.repository;

import com.example.demo.qc.domain.DefectRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DefectRecordRepository extends JpaRepository<DefectRecord, UUID> {
    List<DefectRecord> findByInspectionId(UUID inspectionId);
}

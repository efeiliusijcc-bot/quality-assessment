package com.example.demo.core.repository;

import com.example.demo.core.domain.Workstation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface WorkstationRepository extends JpaRepository<Workstation, UUID> {
    Optional<Workstation> findByStationCode(String stationCode);
}

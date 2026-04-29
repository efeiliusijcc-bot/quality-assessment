package com.example.demo.kg.repository;

import com.example.demo.kg.domain.GraphVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GraphVersionRepository extends JpaRepository<GraphVersion, UUID> {
    Optional<GraphVersion> findByGraphNameAndVersionNo(String graphName, String versionNo);
    List<GraphVersion> findTop1ByOrderByCreatedAtDesc();
}

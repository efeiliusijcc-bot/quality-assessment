package com.example.demo.core.repository;

import com.example.demo.core.domain.FileResource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface FileResourceRepository extends JpaRepository<FileResource, UUID> {}

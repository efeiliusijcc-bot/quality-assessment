package com.example.demo.etl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.neo4j.driver.Driver;

import java.util.List;
import java.util.Map;

@SpringBootTest
@ActiveProfiles("test")
class CheckSchemaTest {

    @MockitoBean
    private Driver neo4jDriver;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void checkProductionBatchSchema() {
        // Check if table exists and get column info
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
            "SELECT column_name, data_type, is_nullable FROM information_schema.columns " +
            "WHERE table_schema = 'prod' AND table_name = 'production_batch' ORDER BY ordinal_position"
        );
        System.out.println("=== prod.production_batch columns ===");
        for (Map<String, Object> col : columns) {
            System.out.println("  " + col.get("column_name") + " | " + col.get("data_type") + " | " + col.get("is_nullable"));
        }
        
        // Try a simple insert
        try {
            jdbcTemplate.update(
                "INSERT INTO prod.production_batch (batch_id, batch_no, product_type_id, plan_qty, actual_qty, start_time, end_time, batch_status, created_at, metadata) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                java.util.UUID.randomUUID(), "TEST-SCHEMA-" + System.nanoTime(), java.util.UUID.randomUUID(), 100, 95,
                null, null, "CREATED", java.sql.Timestamp.from(java.time.Instant.now()), "{}"
            );
            System.out.println("INSERT succeeded!");
        } catch (Exception e) {
            System.out.println("INSERT failed: " + e.getMessage());
        }
    }
}

package com.example.demo.etl;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles("test")
class DebugImportTest {

    @MockitoBean
    private Driver neo4jDriver;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testDirectInsert_defectType() {
        String defectCode = "VOID-" + System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO qc.defect_type (defect_type_id, step_id, defect_code, defect_name, defect_category, default_severity, description, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), defectCode, "Solder Void", "Soldering Defect", 3,
                "Void in solder joint", Timestamp.from(Instant.now())
        );
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT defect_code FROM qc.defect_type WHERE defect_code = ?", defectCode);
        assertFalse(rows.isEmpty());
    }

    @Test
    void testDirectInsert_productionBatch_simple() {
        String batchNo = "B-DBG-" + System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO prod.production_batch (batch_id, batch_no, product_type_id, plan_qty, actual_qty, start_time, end_time, batch_status, created_at, metadata) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), batchNo, UUID.randomUUID(), 100, 95,
                null, null, "CREATED", Timestamp.from(Instant.now()), "{}"
        );
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT batch_no FROM prod.production_batch WHERE batch_no = ?", batchNo);
        assertFalse(rows.isEmpty());
    }
}

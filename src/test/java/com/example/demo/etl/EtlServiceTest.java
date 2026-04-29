package com.example.demo.etl;

import com.example.demo.etl.dto.EtlDtos.*;
import com.example.demo.etl.service.EtlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.neo4j.driver.Driver;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EtlServiceTest {

    @MockitoBean
    private Driver neo4jDriver;

    @Autowired
    private EtlService etlService;

    @Test
    void testSubmitOnlineUpload() {
        OnlineUploadPayload payload = new OnlineUploadPayload(
                "Station-A", "BATCH-001", "DEVICE-01", "100Hz", "mapping-config"
        );
        OnlineUploadResult result = etlService.submitOnlineUpload(payload);

        assertNotNull(result);
        assertNotNull(result.taskId());
        assertEquals("Station-A", result.station());
        assertEquals("BATCH-001", result.batchNo());
        assertEquals("DEVICE-01", result.deviceId());
        assertEquals("100Hz", result.frequency());
        assertEquals("mapping-config", result.mapping());
    }

    @Test
    void testImportManufacturingData() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "manufacturing_data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy-content".getBytes()
        );
        ManufacturingImportSummary summary = etlService.importManufacturingData(file);

        assertNotNull(summary);
        assertEquals("manufacturing_data.xlsx", summary.fileName());
        assertEquals(30, summary.processSettingCount());
        assertEquals(40, summary.equipmentOperationCount());
        assertEquals(25, summary.qualityDefectCount());
    }

    @Test
    void testGetUploadStatistics() {
        // With no import jobs, should return zero stats
        UploadStatisticsResponse stats = etlService.getUploadStatistics();

        assertNotNull(stats);
        assertEquals(0, stats.totalTasks());
        assertNull(stats.latestSyncTime());
    }

    @Test
    void testGetUploadStatisticsAfterUpload() {
        // Submit an upload first, then check statistics
        etlService.submitOnlineUpload(new OnlineUploadPayload(
                "Station-B", "BATCH-002", "DEVICE-02", "200Hz", null
        ));

        UploadStatisticsResponse stats = etlService.getUploadStatistics();

        assertNotNull(stats);
        assertEquals(1, stats.totalTasks());
    }

    @Test
    void testCreateCleaningRule() {
        CreateCleaningRuleRequest request = new CreateCleaningRuleRequest(
                "RULE_001", "Remove Outliers", "temperature",
                "value > 1000 || value < -100", "set_null", 1
        );
        CleaningRuleResponse response = etlService.createCleaningRule(request);

        assertNotNull(response);
        assertNotNull(response.ruleId());
        assertEquals("RULE_001", response.ruleCode());
        assertEquals("Remove Outliers", response.ruleName());
        assertEquals("temperature", response.targetCategory());
        assertEquals("value > 1000 || value < -100", response.conditionExpr());
        assertEquals("set_null", response.actionExpr());
        assertEquals(1, response.priorityNo());
    }

    @Test
    void testCreateImportJob() {
        CreateImportJobRequest request = new CreateImportJobRequest(
                "CSV", "sensor_data.csv", null, "process_setting"
        );
        ImportJobResponse response = etlService.createImportJob(request);

        assertNotNull(response);
        assertNotNull(response.importId());
        assertEquals("CSV", response.sourceType());
        assertEquals("sensor_data.csv", response.sourceName());
        assertEquals("process_setting", response.targetTable());
    }
}

package com.example.demo.qc;

import com.example.demo.common.exception.BusinessException;
import com.example.demo.qc.dto.QcDtos.*;
import com.example.demo.qc.service.QcService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.neo4j.driver.Driver;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QcServiceTest {

    @MockitoBean
    private Driver neo4jDriver;

    @Autowired
    private QcService qcService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @org.junit.jupiter.api.BeforeEach
    void cleanQcTables() {
        jdbcTemplate.update("DELETE FROM qc.defect_record");
        jdbcTemplate.update("DELETE FROM qc.quality_measurement");
        jdbcTemplate.update("DELETE FROM qc.inspection_task");
        jdbcTemplate.update("DELETE FROM qc.defect_type");
        jdbcTemplate.update("DELETE FROM qc.quality_metric_def");
    }

    @Test
    void testCreateMetricDef() {
        CreateQualityMetricDefRequest request = new CreateQualityMetricDefRequest(
                "TEMP_001", "Temperature Metric", UUID.randomUUID(), "Celsius"
        );
        QualityMetricDefResponse response = qcService.createMetricDef(request);

        assertNotNull(response);
        assertNotNull(response.metricId());
        assertEquals("TEMP_001", response.metricCode());
        assertEquals("Temperature Metric", response.metricName());
        assertEquals("Celsius", response.unit());

        // duplicate metricCode should throw
        assertThrows(BusinessException.class, () -> qcService.createMetricDef(request));
    }

    @Test
    void testCreateDefectType() {
        UUID stepId = UUID.randomUUID();
        CreateDefectTypeRequest request = new CreateDefectTypeRequest(
                "SCRATCH_001", "Surface Scratch", stepId
        );
        DefectTypeResponse response = qcService.createDefectType(request);

        assertNotNull(response);
        assertNotNull(response.defectTypeId());
        assertEquals("SCRATCH_001", response.defectCode());
        assertEquals("Surface Scratch", response.defectName());
        assertEquals(stepId, response.stepId());

        // duplicate defectCode for same step should throw
        assertThrows(BusinessException.class, () -> qcService.createDefectType(request));
    }

    @Test
    void testCreateInspectionTask() {
        UUID runId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();
        CreateInspectionTaskRequest request = new CreateInspectionTaskRequest(
                runId, stepId, "visual_inspection"
        );
        InspectionTaskResponse response = qcService.createInspection(request);

        assertNotNull(response);
        assertNotNull(response.inspectionId());
        assertEquals(runId, response.runId());
        assertEquals(stepId, response.stepId());
        assertEquals("visual_inspection", response.inspectionType());
    }

    @Test
    void testCreateDefectRecord() {
        // Create prerequisite inspection task
        InspectionTaskResponse inspection = qcService.createInspection(
                new CreateInspectionTaskRequest(UUID.randomUUID(), UUID.randomUUID(), "defect_detection")
        );
        // Create prerequisite defect type
        DefectTypeResponse defectType = qcService.createDefectType(
                new CreateDefectTypeRequest("DENT_001", "Surface Dent", UUID.randomUUID())
        );

        CreateDefectRecordRequest request = new CreateDefectRecordRequest(
                inspection.inspectionId(), null, defectType.defectTypeId(), 3
        );
        DefectRecordResponse response = qcService.createDefectRecord(request);

        assertNotNull(response);
        assertNotNull(response.defectId());
        assertEquals(inspection.inspectionId(), response.inspectionId());
        assertEquals(defectType.defectTypeId(), response.defectTypeId());
        assertEquals(3, response.defectCount());
    }

    @Test
    void testGetDefectSamples() {
        // With no data, should return empty list
        List<DefectSampleResponse> samples = qcService.getDefectSamples();
        assertNotNull(samples);
        assertTrue(samples.isEmpty());
    }

    @Test
    void testBatchDetect() {
        List<BatchDetectRequestItem> items = List.of(
                new BatchDetectRequestItem("Sample A", "BATCH-001", "http://example.com/a.jpg"),
                new BatchDetectRequestItem("Sample B", "BATCH-001", "http://example.com/b.jpg")
        );
        BatchDetectResponse response = qcService.batchDetect(items);

        assertNotNull(response);
        assertEquals(2, response.total());
        assertEquals(2, response.results().size());
        assertEquals("Batch detection completed", response.message());

        DefectSampleResponse first = response.results().get(0);
        assertEquals("Sample A", first.name());
        assertFalse(first.results().isEmpty());
        assertFalse(first.defects().isEmpty());
    }

    @Test
    void testGetDefectStatistics() {
        // With no data, should return defaults
        DefectStatisticsResponse stats = qcService.getDefectStatistics();

        assertNotNull(stats);
        assertEquals(0, stats.totalSamples());
        assertEquals(0.0, stats.avgConfidence());
        assertEquals("v1.0", stats.modelVersion());
    }
}

package com.example.demo.eval;

import com.example.demo.common.exception.BusinessException;
import com.example.demo.eval.dto.EvalDtos.*;
import com.example.demo.eval.service.EvalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class EvalServiceTest {

    @MockitoBean
    private Driver neo4jDriver;

    @Autowired
    private EvalService evalService;

    @Test
    void testCreateAssessmentTask() {
        UUID batchId = UUID.randomUUID();
        CreateAssessmentRequest request = new CreateAssessmentRequest(
                "QUALITY_CHECK", batchId, null
        );
        AssessmentTaskResponse response = evalService.createTask(request);

        assertNotNull(response);
        assertNotNull(response.taskId());
        assertEquals("QUALITY_CHECK", response.taskType());
        assertEquals(batchId, response.batchId());
        assertNotNull(response.taskStatus());
    }

    @Test
    void testGetAssessmentTask() {
        // Create a task first
        UUID batchId = UUID.randomUUID();
        CreateAssessmentRequest request = new CreateAssessmentRequest(
                "DEFECT_ANALYSIS", batchId, null
        );
        AssessmentTaskResponse created = evalService.createTask(request);

        // Retrieve it
        AssessmentTaskResponse fetched = evalService.getTaskById(created.taskId());

        assertNotNull(fetched);
        assertEquals(created.taskId(), fetched.taskId());
        assertEquals("DEFECT_ANALYSIS", fetched.taskType());
        assertEquals(batchId, fetched.batchId());

        // Non-existent ID should throw
        assertThrows(BusinessException.class, () -> evalService.getTaskById(UUID.randomUUID()));
    }

    @Test
    void testGetQualifiedDashboard() {
        QualifiedDashboardData data = evalService.getQualifiedDashboard(null);

        assertNotNull(data);
        assertNotNull(data.metrics());
        assertFalse(data.metrics().isEmpty());
        assertNotNull(data.timeAxis());
        assertNotNull(data.temperatureData());
        assertNotNull(data.graphReasoning());
    }

    @Test
    void testGetJudgmentDashboard() {
        JudgmentDashboardData data = evalService.getJudgmentDashboard(null);

        assertNotNull(data);
        assertNotNull(data.metrics());
        assertFalse(data.metrics().isEmpty());
        assertNotNull(data.radarIndicators());
        assertNotNull(data.diagnosisItems());
        assertNotNull(data.actionItems());
        assertNotNull(data.graphReasoning());
    }

    @Test
    void testGetPredictionDashboard() {
        PredictionDashboardData data = evalService.getPredictionDashboard(null);

        assertNotNull(data);
        assertNotNull(data.metrics());
        assertFalse(data.metrics().isEmpty());
        assertEquals(0.5, data.threshold());
        assertNotNull(data.triggerCards());
        assertNotNull(data.optimizationTable());
        assertNotNull(data.graphReasoning());
    }

    @Test
    void testGetStations() {
        // With no data, should return empty list
        List<String> stations = evalService.getStations();

        assertNotNull(stations);
        // May be empty if no workstations configured, that is acceptable
    }

    @Test
    void testGetBatches() {
        // With no data, should return empty list
        List<String> batches = evalService.getBatches();

        assertNotNull(batches);
        // May be empty if no batches configured, that is acceptable
    }

    @Test
    void testRunOptimization() {
        UUID batchId = UUID.randomUUID();

        OptimizationResponse response = evalService.runOptimization(batchId.toString());

        assertNotNull(response);
        assertEquals(batchId.toString(), response.batchId());
        assertEquals("MANSga3", response.algorithm());
        assertNotNull(response.paretoFront());
        assertNotNull(response.recommendedSolution());
        assertNotNull(response.statistics());
        assertTrue(response.statistics().totalEvaluations() > 0);
    }
}

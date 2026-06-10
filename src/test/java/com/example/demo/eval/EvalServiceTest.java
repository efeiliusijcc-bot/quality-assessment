package com.example.demo.eval;

import com.example.demo.common.exception.BusinessException;
import com.example.demo.core.domain.ParameterDef;
import com.example.demo.core.domain.Workstation;
import com.example.demo.core.repository.ParameterDefRepository;
import com.example.demo.core.repository.WorkstationRepository;
import com.example.demo.eval.dto.EvalDtos.*;
import com.example.demo.eval.service.EvalService;
import com.example.demo.prod.domain.ParameterValue;
import com.example.demo.prod.domain.ProcessRun;
import com.example.demo.prod.domain.ProductionBatch;
import com.example.demo.prod.repository.ParameterValueRepository;
import com.example.demo.prod.repository.ProcessRunRepository;
import com.example.demo.prod.repository.ProductionBatchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.neo4j.driver.Driver;

import java.math.BigDecimal;
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

    @Autowired
    private ProductionBatchRepository batchRepository;

    @Autowired
    private ProcessRunRepository processRunRepository;

    @Autowired
    private ParameterValueRepository parameterValueRepository;

    @Autowired
    private ParameterDefRepository parameterDefRepository;

    @Autowired
    private WorkstationRepository workstationRepository;

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
    void testAssessmentStreamsUseParameterDefinitionsInsteadOfValueOrder() {
        UUID stepId = UUID.randomUUID();
        ProductionBatch batch = batchRepository.save(new ProductionBatch("BATCH-EVAL-SIGNALS", UUID.randomUUID()));
        Workstation station = workstationRepository.save(new Workstation(stepId, "AOI-EVAL", "AOI评估工位"));

        ParameterDef currentDef = parameterDefRepository.save(new ParameterDef("motor_current", "工作电流", "PROCESS", "NUMBER"));
        ParameterDef pressureDef = parameterDefRepository.save(new ParameterDef("mount_pressure", "贴装压力", "PROCESS", "NUMBER"));
        ParameterDef temperatureDef = parameterDefRepository.save(new ParameterDef("reflow_temperature", "回流温度", "PROCESS", "NUMBER"));
        ParameterDef beltDef = parameterDefRepository.save(new ParameterDef("belt_speed", "链速", "PROCESS", "NUMBER"));
        currentDef.setStepId(stepId);
        pressureDef.setStepId(stepId);
        temperatureDef.setStepId(stepId);
        beltDef.setStepId(stepId);
        parameterDefRepository.saveAll(List.of(currentDef, pressureDef, temperatureDef, beltDef));

        ProcessRun run = new ProcessRun(batch.getBatchId(), stepId);
        run.setStationId(station.getStationId());
        run = processRunRepository.save(run);

        parameterValueRepository.save(new ParameterValue(run.getRunId(), currentDef.getParamId(), new BigDecimal("1.18")));
        parameterValueRepository.save(new ParameterValue(run.getRunId(), pressureDef.getParamId(), new BigDecimal("4.20")));
        parameterValueRepository.save(new ParameterValue(run.getRunId(), temperatureDef.getParamId(), new BigDecimal("236.50")));
        parameterValueRepository.save(new ParameterValue(run.getRunId(), beltDef.getParamId(), new BigDecimal("88.00")));

        AssessmentHistoryPage history = evalService.getAssessmentHistory(batch.getBatchId(), 1, 10);
        assertEquals(1, history.total());
        AssessmentHistoryItem item = history.records().get(0);
        assertEquals("BATCH-EVAL-SIGNALS", item.batchId());
        assertEquals("AOI评估工位", item.station());
        assertEquals(236.5, item.temperature());
        assertEquals(4.2, item.pressure());
        assertEquals(1.18, item.currentValue());

        JudgmentStreamData stream = evalService.getJudgmentStream(batch.getBatchId());
        assertEquals(List.of(236.5), stream.temperature());
        assertEquals(List.of(88.0), stream.beltSpeed());
        assertEquals(List.of(1.18), stream.current());

        QualifiedDashboardData dashboard = evalService.getQualifiedDashboard("BATCH-EVAL-SIGNALS");
        assertEquals(List.of(236.5), dashboard.temperatureData());
        assertEquals(List.of(4.2), dashboard.pressureData());
        assertEquals(List.of(1.18), dashboard.currentData());
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

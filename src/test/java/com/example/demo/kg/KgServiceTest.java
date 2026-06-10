package com.example.demo.kg;

import com.example.demo.core.domain.ParameterDef;
import com.example.demo.core.domain.ProcessStep;
import com.example.demo.core.repository.ParameterDefRepository;
import com.example.demo.core.repository.ProcessStepRepository;
import com.example.demo.kg.dto.KgDtos.GraphAnalysisResponse;
import com.example.demo.kg.dto.KgDtos.GraphPathSearchResponse;
import com.example.demo.kg.dto.KgDtos.GraphVisualizationResponse;
import com.example.demo.kg.service.KgService;
import com.example.demo.prod.domain.ParameterValue;
import com.example.demo.prod.domain.ProcessRun;
import com.example.demo.prod.domain.ProductionBatch;
import com.example.demo.prod.repository.ParameterValueRepository;
import com.example.demo.prod.repository.ProcessRunRepository;
import com.example.demo.prod.repository.ProductionBatchRepository;
import com.example.demo.qc.domain.DefectRecord;
import com.example.demo.qc.domain.DefectType;
import com.example.demo.qc.domain.InspectionTask;
import com.example.demo.qc.repository.DefectRecordRepository;
import com.example.demo.qc.repository.DefectTypeRepository;
import com.example.demo.qc.repository.InspectionTaskRepository;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KgServiceTest {

    @MockitoBean
    private Driver neo4jDriver;

    @Autowired
    private KgService kgService;

    @Autowired
    private ProcessStepRepository processStepRepository;

    @Autowired
    private ParameterDefRepository parameterDefRepository;

    @Autowired
    private ProductionBatchRepository productionBatchRepository;

    @Autowired
    private ProcessRunRepository processRunRepository;

    @Autowired
    private ParameterValueRepository parameterValueRepository;

    @Autowired
    private InspectionTaskRepository inspectionTaskRepository;

    @Autowired
    private DefectTypeRepository defectTypeRepository;

    @Autowired
    private DefectRecordRepository defectRecordRepository;

    @Test
    void testBatchNoFullVisualizationFallsBackToPostgresSubgraph() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ProcessStep step = processStepRepository.save(new ProcessStep("KG-" + suffix, "Lamination", 10, true));
        ParameterDef temperature = new ParameterDef("TEMP-" + suffix, "Temperature", "PROCESS", "NUMBER");
        temperature.setStepId(step.getStepId());
        parameterDefRepository.save(temperature);

        ProductionBatch batch = productionBatchRepository.save(new ProductionBatch("KG-BATCH-" + suffix, UUID.randomUUID()));
        ProcessRun run = processRunRepository.save(new ProcessRun(batch.getBatchId(), step.getStepId()));
        parameterValueRepository.save(new ParameterValue(run.getRunId(), temperature.getParamId(), new BigDecimal("236.5")));

        InspectionTask inspection = inspectionTaskRepository.save(new InspectionTask(run.getRunId(), step.getStepId(), "VISUAL"));
        DefectType bubble = defectTypeRepository.save(
                new DefectType(step.getStepId(), "BUBBLE-" + suffix, "Bubble", "SURFACE", 4));
        DefectRecord defect = new DefectRecord(inspection.getInspectionId(), bubble.getDefectTypeId());
        defect.setSeverityLevel(4);
        defect.setConfidence(new BigDecimal("0.91"));
        defectRecordRepository.save(defect);

        GraphVisualizationResponse graph = kgService.getGraphVisualization(batch.getBatchNo(), true);

        assertFalse(graph.nodes().isEmpty());
        assertTrue(graph.nodes().stream().anyMatch(node -> "ProductionBatch".equals(node.label())));
        assertTrue(graph.nodes().stream().anyMatch(node -> "ParameterDef".equals(node.label()) && "Temperature".equals(node.name())));
        assertTrue(graph.nodes().stream().anyMatch(node -> "DefectType".equals(node.label()) && "Bubble".equals(node.name())));
        assertTrue(graph.edges().stream().anyMatch(edge -> "HAS_PARAMETER".equals(edge.type())));
        assertTrue(graph.edges().stream().anyMatch(edge -> "ASSOCIATED_WITH_DEFECT".equals(edge.type())));

        GraphAnalysisResponse analysis = kgService.getGraphAnalysis(batch.getBatchNo());
        assertTrue(analysis.filterOptions().parameters().contains("Temperature"));
        assertTrue(analysis.filterOptions().defects().contains("Bubble"));
        assertFalse(analysis.ruleRelations().isEmpty());

        GraphPathSearchResponse path = kgService.searchGraphPath(batch.getBatchNo(), batch.getBatchNo(), "Bubble");
        assertEquals(batch.getBatchNo(), path.batchId());
        assertFalse(path.nodes().isEmpty());
        assertFalse(path.edges().isEmpty());
    }
}

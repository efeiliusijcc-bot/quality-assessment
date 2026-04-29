package com.example.demo.prod;

import com.example.demo.common.exception.BusinessException;
import com.example.demo.core.dto.CoreDtos.*;
import com.example.demo.core.service.CoreService;
import com.example.demo.prod.dto.ProductionDtos.*;
import com.example.demo.prod.service.ProductionBatchService;
import com.example.demo.prod.service.ProductionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.neo4j.driver.Driver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductionServiceTest {

    @MockitoBean
    private Driver neo4jDriver;

    @Autowired
    private ProductionBatchService batchService;

    @Autowired
    private ProductionService productionService;

    @Autowired
    private CoreService coreService;

    private UUID createTestProductTypeId() {
        ProductTypeResponse pt = coreService.createProductType(
                new CreateProductTypeRequest("PT01", "电池模组", "三元锂"));
        return pt.productTypeId();
    }

    private UUID createTestStepId() {
        ProcessStepResponse step = coreService.createProcessStep(
                new CreateProcessStepRequest("STEP01", "焊接", 1, false, null));
        return step.stepId();
    }

    @Test
    void testCreateBatch() {
        UUID productTypeId = createTestProductTypeId();
        CreateBatchRequest req = new CreateBatchRequest("BATCH-001", productTypeId, 100);
        BatchResponse resp = batchService.createBatch(req);
        assertNotNull(resp);
        assertNotNull(resp.batchId());
        assertEquals("BATCH-001", resp.batchNo());
        assertEquals(100, resp.planQty());
    }

    @Test
    void testCreateBatchDuplicateNo() {
        UUID productTypeId = createTestProductTypeId();
        batchService.createBatch(new CreateBatchRequest("BATCH-001", productTypeId, 100));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> batchService.createBatch(new CreateBatchRequest("BATCH-001", productTypeId, 50)));
        assertEquals(400, ex.getCode());
    }

    @Test
    void testGetBatchByNo() {
        UUID productTypeId = createTestProductTypeId();
        batchService.createBatch(new CreateBatchRequest("BATCH-001", productTypeId, 100));

        BatchResponse resp = batchService.getBatchByNo("BATCH-001");
        assertNotNull(resp);
        assertEquals("BATCH-001", resp.batchNo());
    }

    @Test
    void testCreateUnit() {
        UUID productTypeId = createTestProductTypeId();
        BatchResponse batch = batchService.createBatch(
                new CreateBatchRequest("BATCH-001", productTypeId, 100));

        CreateProductUnitRequest req = new CreateProductUnitRequest(batch.batchId(), "SN-001");
        ProductUnitResponse resp = productionService.createUnit(req);
        assertNotNull(resp);
        assertNotNull(resp.unitId());
        assertEquals(batch.batchId(), resp.batchId());
        assertEquals("SN-001", resp.serialNo());
    }

    @Test
    void testCreateRecipe() {
        UUID stepId = createTestStepId();
        UUID productTypeId = createTestProductTypeId();

        CreateProcessRecipeRequest req = new CreateProcessRecipeRequest(
                "RECIPE-01", "焊接标准配方", productTypeId, stepId, null);
        ProcessRecipeResponse resp = productionService.createRecipe(req);
        assertNotNull(resp);
        assertNotNull(resp.recipeId());
        assertEquals("RECIPE-01", resp.recipeCode());
        assertEquals("焊接标准配方", resp.recipeName());
        assertEquals(stepId, resp.stepId());
        assertEquals(productTypeId, resp.productTypeId());
    }

    @Test
    void testCreateRun() {
        UUID productTypeId = createTestProductTypeId();
        BatchResponse batch = batchService.createBatch(
                new CreateBatchRequest("BATCH-001", productTypeId, 100));
        UUID stepId = createTestStepId();

        CreateProcessRunRequest req = new CreateProcessRunRequest(
                batch.batchId(), stepId, null, null, null, null);
        ProcessRunDetailResponse resp = productionService.createRun(req);
        assertNotNull(resp);
        assertNotNull(resp.runId());
        assertEquals(batch.batchId(), resp.batchId());
        assertEquals(stepId, resp.stepId());
    }

    @Test
    void testCreateParameterValue() {
        // Create step, batch, and run to get a valid runId
        UUID productTypeId = createTestProductTypeId();
        BatchResponse batch = batchService.createBatch(
                new CreateBatchRequest("BATCH-001", productTypeId, 100));
        UUID stepId = createTestStepId();
        ProcessRunDetailResponse run = productionService.createRun(
                new CreateProcessRunRequest(batch.batchId(), stepId, null, null, null, null));

        // Create a parameter def to get a valid paramId
        ParameterDefResponse paramDef = coreService.createParameterDef(
                new CreateParameterDefRequest("TEMP01", "焊接温度", stepId, "过程参数", "NUMBER", "℃"));

        CreateParameterValueRequest req = new CreateParameterValueRequest(
                run.runId(), paramDef.paramId(), 250.5, null);
        ParameterValueDetailResponse resp = productionService.createParameterValue(req);
        assertNotNull(resp);
        assertNotNull(resp.valueId());
        assertEquals(run.runId(), resp.runId());
        assertEquals(paramDef.paramId(), resp.paramId());
        assertEquals(0, resp.valueNum().compareTo(new java.math.BigDecimal("250.5")));
    }
}

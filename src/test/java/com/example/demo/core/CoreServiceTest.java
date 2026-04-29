package com.example.demo.core;

import com.example.demo.common.exception.BusinessException;
import com.example.demo.core.dto.CoreDtos.*;
import com.example.demo.core.service.CoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.neo4j.driver.Driver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoreServiceTest {

    @MockitoBean
    private Driver neo4jDriver;

    @Autowired
    private CoreService coreService;

    @Test
    void testCreateProcessStep() {
        CreateProcessStepRequest req = new CreateProcessStepRequest("STEP01", "焊接", 1, false, "焊接工序");
        ProcessStepResponse resp = coreService.createProcessStep(req);
        assertNotNull(resp);
        assertNotNull(resp.stepId());
        assertEquals("STEP01", resp.stepCode());
        assertEquals("焊接", resp.stepName());
        assertEquals(1, resp.stepOrder());
        assertFalse(resp.isInspection());
        assertEquals("焊接工序", resp.description());
    }

    @Test
    void testCreateProcessStepDuplicate() {
        CreateProcessStepRequest req = new CreateProcessStepRequest("STEP01", "焊接", 1, false, "焊接工序");
        coreService.createProcessStep(req);

        CreateProcessStepRequest dup = new CreateProcessStepRequest("STEP01", "焊接2", 2, false, "重复");
        BusinessException ex = assertThrows(BusinessException.class, () -> coreService.createProcessStep(dup));
        assertEquals(400, ex.getCode());
    }

    @Test
    void testGetProcessStepNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> coreService.getProcessStep(java.util.UUID.randomUUID()));
        assertEquals(404, ex.getCode());
    }

    @Test
    void testCreateWorkstation() {
        // Create a process step first to reference
        ProcessStepResponse step = coreService.createProcessStep(
                new CreateProcessStepRequest("STEP01", "焊接", 1, false, null));

        CreateWorkstationRequest req = new CreateWorkstationRequest("WS01", "焊接工位1", step.stepId(), "A区");
        WorkstationResponse resp = coreService.createWorkstation(req);
        assertNotNull(resp);
        assertNotNull(resp.stationId());
        assertEquals("WS01", resp.stationCode());
        assertEquals("焊接工位1", resp.stationName());
        assertEquals(step.stepId(), resp.stepId());
        assertEquals("A区", resp.location());
    }

    @Test
    void testCreateEquipment() {
        // Create step -> workstation -> equipment
        ProcessStepResponse step = coreService.createProcessStep(
                new CreateProcessStepRequest("STEP01", "焊接", 1, false, null));
        WorkstationResponse ws = coreService.createWorkstation(
                new CreateWorkstationRequest("WS01", "焊接工位1", step.stepId(), null));

        CreateEquipmentRequest req = new CreateEquipmentRequest("EQ01", "焊接机器人", ws.stationId(), "焊接设备");
        EquipmentResponse resp = coreService.createEquipment(req);
        assertNotNull(resp);
        assertNotNull(resp.equipmentId());
        assertEquals("EQ01", resp.equipmentCode());
        assertEquals("焊接机器人", resp.equipmentName());
        assertEquals(ws.stationId(), resp.stationId());
        assertEquals("焊接设备", resp.equipmentType());
    }

    @Test
    void testCreateProductType() {
        CreateProductTypeRequest req = new CreateProductTypeRequest("PT01", "电池模组", "三元锂");
        ProductTypeResponse resp = coreService.createProductType(req);
        assertNotNull(resp);
        assertNotNull(resp.productTypeId());
        assertEquals("PT01", resp.productCode());
        assertEquals("电池模组", resp.productName());
        assertEquals("三元锂", resp.materialSystem());
    }

    @Test
    void testCreateParameterDef() {
        // Create a process step first
        ProcessStepResponse step = coreService.createProcessStep(
                new CreateProcessStepRequest("STEP01", "焊接", 1, false, null));

        CreateParameterDefRequest req = new CreateParameterDefRequest(
                "TEMP01", "焊接温度", step.stepId(), "过程参数", "NUMBER", "℃");
        ParameterDefResponse resp = coreService.createParameterDef(req);
        assertNotNull(resp);
        assertNotNull(resp.paramId());
        assertEquals("TEMP01", resp.paramCode());
        assertEquals("焊接温度", resp.paramName());
        assertEquals(step.stepId(), resp.stepId());
        assertEquals("过程参数", resp.paramCategory());
        assertEquals("NUMBER", resp.dataType());
        assertEquals("℃", resp.unit());
    }

    @Test
    void testListProcessSteps() {
        coreService.createProcessStep(new CreateProcessStepRequest("STEP01", "焊接", 1, false, null));
        coreService.createProcessStep(new CreateProcessStepRequest("STEP02", "涂胶", 2, false, null));
        coreService.createProcessStep(new CreateProcessStepRequest("STEP03", "检测", 3, true, "终检"));

        List<ProcessStepResponse> list = coreService.listProcessSteps();
        assertEquals(3, list.size());
    }
}

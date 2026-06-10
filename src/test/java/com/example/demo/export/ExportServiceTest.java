package com.example.demo.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.core.domain.Workstation;
import com.example.demo.core.repository.WorkstationRepository;
import com.example.demo.export.dto.ExportDtos.ExportPageResult;
import com.example.demo.export.dto.ExportDtos.ExportSearchParams;
import com.example.demo.export.service.ExportService;
import com.example.demo.prod.domain.ProcessRun;
import com.example.demo.prod.domain.ProductionBatch;
import com.example.demo.prod.repository.ProcessRunRepository;
import com.example.demo.prod.repository.ProductionBatchRepository;
import com.example.demo.qc.domain.DefectRecord;
import com.example.demo.qc.domain.DefectType;
import com.example.demo.qc.domain.InspectionTask;
import com.example.demo.qc.repository.DefectRecordRepository;
import com.example.demo.qc.repository.DefectTypeRepository;
import com.example.demo.qc.repository.InspectionTaskRepository;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExportServiceTest {

    @MockitoBean
    private Driver neo4jDriver;

    @Autowired
    private ExportService exportService;

    @Autowired
    private ProductionBatchRepository productionBatchRepository;

    @Autowired
    private ProcessRunRepository processRunRepository;

    @Autowired
    private WorkstationRepository workstationRepository;

    @Autowired
    private InspectionTaskRepository inspectionTaskRepository;

    @Autowired
    private DefectTypeRepository defectTypeRepository;

    @Autowired
    private DefectRecordRepository defectRecordRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void exportsFilteredResultsAsRealExcelAndPdf() throws Exception {
        UUID stepId = UUID.randomUUID();
        ProductionBatch batch = productionBatchRepository.save(new ProductionBatch("BATCH-EXPORT-001", UUID.randomUUID()));
        Workstation station = workstationRepository.save(new Workstation(stepId, "ST-EXPORT", "AOI检测工位"));

        ProcessRun run = new ProcessRun(batch.getBatchId(), stepId);
        run.setStationId(station.getStationId());
        run = processRunRepository.save(run);

        InspectionTask task = new InspectionTask(run.getRunId(), stepId, "VISION");
        task.setResultStatus("fail");
        task.setConfidence(new BigDecimal("0.910000"));
        task = inspectionTaskRepository.save(task);

        DefectType defectType = defectTypeRepository.save(new DefectType(stepId, "SOLDER_BRIDGE", "连锡", "焊接", 4));
        DefectRecord defect = new DefectRecord(task.getInspectionId(), defectType.getDefectTypeId());
        defect.setConfidence(new BigDecimal("0.870000"));
        defectRecordRepository.save(defect);
        entityManager.flush();

        ExportSearchParams query = new ExportSearchParams("BATCH-EXPORT", "AOI检测工位", "fail", List.of(), 1, 10);
        ExportPageResult records = exportService.getRecords(query);

        assertEquals(1, records.total());
        assertEquals("BATCH-EXPORT-001", records.list().get(0).batchId());
        assertEquals("AOI检测工位", records.list().get(0).station());
        assertEquals("连锡", records.list().get(0).defectType());

        byte[] excel = exportService.generateExcelBytes(query);
        assertTrue(excel.length > 1024);
        assertEquals('P', excel[0]);
        assertEquals('K', excel[1]);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertNotNull(workbook.getSheet("Assessment Results"));
            assertEquals("生产批次", workbook.getSheetAt(0).getRow(0).getCell(2).getStringCellValue());
            assertEquals("BATCH-EXPORT-001", workbook.getSheetAt(0).getRow(1).getCell(2).getStringCellValue());
            assertEquals("连锡", workbook.getSheetAt(0).getRow(1).getCell(4).getStringCellValue());
        }

        byte[] pdf = exportService.generatePdfBytes(query);
        assertTrue(pdf.length > 1024);
        assertEquals("%PDF-", new String(pdf, 0, 5));
    }
}

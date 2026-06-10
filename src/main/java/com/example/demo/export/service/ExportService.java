package com.example.demo.export.service;

import com.example.demo.core.domain.Workstation;
import com.example.demo.core.repository.WorkstationRepository;
import com.example.demo.export.dto.ExportDtos.ExportFileResponse;
import com.example.demo.export.dto.ExportDtos.ExportPageResult;
import com.example.demo.export.dto.ExportDtos.ExportRecord;
import com.example.demo.export.dto.ExportDtos.ExportSearchParams;
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
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final ProcessRunRepository processRunRepository;
    private final DefectRecordRepository defectRecordRepository;
    private final DefectTypeRepository defectTypeRepository;
    private final InspectionTaskRepository inspectionTaskRepository;
    private final ProductionBatchRepository productionBatchRepository;
    private final WorkstationRepository workstationRepository;
    private final JdbcTemplate jdbcTemplate;

    public ExportService(
            ProcessRunRepository processRunRepository,
            DefectRecordRepository defectRecordRepository,
            DefectTypeRepository defectTypeRepository,
            InspectionTaskRepository inspectionTaskRepository,
            ProductionBatchRepository productionBatchRepository,
            WorkstationRepository workstationRepository,
            JdbcTemplate jdbcTemplate) {
        this.processRunRepository = processRunRepository;
        this.defectRecordRepository = defectRecordRepository;
        this.defectTypeRepository = defectTypeRepository;
        this.inspectionTaskRepository = inspectionTaskRepository;
        this.productionBatchRepository = productionBatchRepository;
        this.workstationRepository = workstationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ExportPageResult getRecords(ExportSearchParams params) {
        ExportSearchParams query = normalizeParams(params);
        int page = query.page() <= 0 ? 1 : query.page();
        int pageSize = query.pageSize() <= 0 ? 10 : query.pageSize();
        List<ExportRecord> filtered = findFilteredRecords(query);
        int total = filtered.size();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        return new ExportPageResult(filtered.subList(from, to), total);
    }

    public ExportFileResponse exportExcel(ExportSearchParams params) {
        log.info("Creating Excel export task for batchId={}, station={}", params.batchId(), params.station());
        return new ExportFileResponse("assessment.xlsx");
    }

    public ExportFileResponse exportPdf(ExportSearchParams params) {
        log.info("Creating PDF export task for batchId={}, station={}", params.batchId(), params.station());
        return new ExportFileResponse("assessment-report.pdf");
    }

    public byte[] generateExcelBytes(ExportSearchParams params) {
        List<ExportRecord> records = findFilteredRecords(normalizeParams(params));
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Assessment Results");
            CellStyle headerStyle = createExcelHeaderStyle(workbook);
            CellStyle percentStyle = workbook.createCellStyle();
            percentStyle.setDataFormat(workbook.createDataFormat().getFormat("0.0%"));

            String[] headers = {"ID", "评估时间", "生产批次", "工位", "主要缺陷类型", "AI 置信度", "状态"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (ExportRecord record : records) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(record.id());
                row.createCell(1).setCellValue(record.date());
                row.createCell(2).setCellValue(record.batchId());
                row.createCell(3).setCellValue(record.station());
                row.createCell(4).setCellValue(record.defectType());
                Cell confidenceCell = row.createCell(5);
                confidenceCell.setCellValue(record.confidence());
                confidenceCell.setCellStyle(percentStyle);
                row.createCell(6).setCellValue(toStatusLabel(record.status()));
            }

            int[] widths = {38, 20, 20, 18, 22, 14, 12};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate Excel export", e);
        }
    }

    public byte[] generatePdfBytes(ExportSearchParams params) {
        List<ExportRecord> records = findFilteredRecords(normalizeParams(params));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            Font metaFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
            Paragraph title = new Paragraph("Assessment Export Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph("Generated at: " + formatInstant(Instant.now()), metaFont));
            document.add(new Paragraph("Total records: " + records.size(), metaFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(new float[] {2.2f, 2.2f, 1.6f, 1.8f, 2.0f, 1.1f, 1.0f});
            table.setWidthPercentage(100);
            addPdfHeader(table, "ID");
            addPdfHeader(table, "Date");
            addPdfHeader(table, "Batch");
            addPdfHeader(table, "Station");
            addPdfHeader(table, "Defect");
            addPdfHeader(table, "Confidence");
            addPdfHeader(table, "Status");

            for (ExportRecord record : records) {
                table.addCell(trimForPdf(record.id(), 36));
                table.addCell(record.date());
                table.addCell(record.batchId());
                table.addCell(record.station());
                table.addCell(record.defectType());
                table.addCell(String.format("%.1f%%", record.confidence() * 100));
                table.addCell(toStatusLabel(record.status()));
            }
            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Failed to generate PDF export", e);
        }
    }

    private List<ExportRecord> findFilteredRecords(ExportSearchParams params) {
        List<ExportRecord> allRecords = jdbcTemplate.query("""
                SELECT CAST(COALESCE(dr.defect_id, it.inspection_id) AS varchar) AS id,
                       it.inspected_at,
                       COALESCE(pb.batch_no, '') AS batch_no,
                       COALESCE(ws.station_name, '') AS station_name,
                       COALESCE(dt.defect_name, 'N/A') AS defect_name,
                       COALESCE(dr.confidence, it.confidence, 0) AS confidence,
                       LOWER(COALESCE(it.result_status, 'pass')) AS result_status
                FROM qc.inspection_task it
                JOIN prod.process_run pr ON pr.run_id = it.run_id
                LEFT JOIN prod.production_batch pb ON pb.batch_id = pr.batch_id
                LEFT JOIN core.workstation ws ON ws.station_id = pr.station_id
                LEFT JOIN qc.defect_record dr ON dr.inspection_id = it.inspection_id
                LEFT JOIN qc.defect_type dt ON dt.defect_type_id = dr.defect_type_id
                ORDER BY it.inspected_at DESC NULLS LAST, id
                """,
                (rs, rowNum) -> new ExportRecord(
                        rs.getString("id"),
                        formatInstant(rs.getTimestamp("inspected_at") == null ? null : rs.getTimestamp("inspected_at").toInstant()),
                        rs.getString("batch_no"),
                        rs.getString("station_name"),
                        rs.getString("defect_name"),
                        rs.getBigDecimal("confidence") == null ? 0.0 : rs.getBigDecimal("confidence").doubleValue(),
                        rs.getString("result_status")
                ));

        return allRecords.stream()
                .filter(record -> matchBatchId(record, params.batchId()))
                .filter(record -> matchStation(record, params.station()))
                .filter(record -> matchStatus(record, params.status()))
                .filter(record -> matchDateRange(record, params.dateRange()))
                .toList();
    }

    private ExportSearchParams normalizeParams(ExportSearchParams params) {
        if (params == null) {
            return new ExportSearchParams(null, null, null, null, 1, 10);
        }
        return params;
    }

    private CellStyle createExcelHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void addPdfHeader(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, new Font(Font.HELVETICA, 9, Font.BOLD)));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private String toStatusLabel(String status) {
        return "pass".equalsIgnoreCase(status) ? "Pass" : "Fail";
    }

    private String trimForPdf(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private boolean matchBatchId(ExportRecord record, String batchId) {
        if (batchId == null || batchId.isBlank()) {
            return true;
        }
        return record.batchId().toLowerCase().contains(batchId.toLowerCase());
    }

    private boolean matchStation(ExportRecord record, String station) {
        if (station == null || station.isBlank()) {
            return true;
        }
        return record.station().equalsIgnoreCase(station);
    }

    private boolean matchStatus(ExportRecord record, String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return true;
        }
        return record.status().equalsIgnoreCase(status);
    }

    private boolean matchDateRange(ExportRecord record, List<String> dateRange) {
        if (dateRange == null || dateRange.size() < 2) {
            return true;
        }
        String startStr = dateRange.get(0);
        String endStr = dateRange.get(1);
        if (startStr == null || endStr == null || startStr.isBlank() || endStr.isBlank()) {
            return true;
        }
        try {
            LocalDate start = LocalDate.parse(startStr);
            LocalDate end = LocalDate.parse(endStr);
            LocalDate recordDate = LocalDate.parse(record.date().substring(0, 10));
            return !recordDate.isBefore(start) && !recordDate.isAfter(end);
        } catch (Exception e) {
            return true;
        }
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "";
        }
        return instant.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}

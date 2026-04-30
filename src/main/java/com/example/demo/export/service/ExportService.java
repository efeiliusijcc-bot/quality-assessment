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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public ExportService(
            ProcessRunRepository processRunRepository,
            DefectRecordRepository defectRecordRepository,
            DefectTypeRepository defectTypeRepository,
            InspectionTaskRepository inspectionTaskRepository,
            ProductionBatchRepository productionBatchRepository,
            WorkstationRepository workstationRepository) {
        this.processRunRepository = processRunRepository;
        this.defectRecordRepository = defectRecordRepository;
        this.defectTypeRepository = defectTypeRepository;
        this.inspectionTaskRepository = inspectionTaskRepository;
        this.productionBatchRepository = productionBatchRepository;
        this.workstationRepository = workstationRepository;
    }

    public ExportPageResult getRecords(ExportSearchParams params) {
        int page = params.page() <= 0 ? 1 : params.page();
        int pageSize = params.pageSize() <= 0 ? 10 : params.pageSize();

        // Load all inspection tasks
        List<InspectionTask> allTasks = inspectionTaskRepository.findAll();

        // Batch-load all related entities in one pass
        List<ProcessRun> allRuns = processRunRepository.findAll();
        Map<UUID, ProcessRun> runMap = new HashMap<>();
        for (ProcessRun run : allRuns) {
            runMap.put(run.getRunId(), run);
        }

        Map<UUID, ProductionBatch> batchMap = new HashMap<>();
        for (ProcessRun run : allRuns) {
            if (run.getBatchId() != null) {
                productionBatchRepository.findById(run.getBatchId())
                    .ifPresent(b -> batchMap.put(run.getBatchId(), b));
            }
        }

        Map<UUID, Workstation> stationMap = new HashMap<>();
        for (ProcessRun run : allRuns) {
            if (run.getStationId() != null) {
                workstationRepository.findById(run.getStationId())
                    .ifPresent(ws -> stationMap.put(run.getStationId(), ws));
            }
        }

        // Batch-load defect records and defect types
        List<DefectRecord> allDefects = defectRecordRepository.findByInspectionIdIn(
                allTasks.stream().map(InspectionTask::getInspectionId).toList());
        Map<UUID, List<DefectRecord>> defectsByInspection = new HashMap<>();
        for (DefectRecord dr : allDefects) {
            defectsByInspection.computeIfAbsent(dr.getInspectionId(), k -> new ArrayList<>()).add(dr);
        }

        Map<UUID, DefectType> defectTypeMap = new HashMap<>();
        defectTypeRepository.findAll().forEach(dt -> defectTypeMap.put(dt.getDefectTypeId(), dt));

        List<ExportRecord> allRecords = new ArrayList<>();

        for (InspectionTask task : allTasks) {
            UUID runId = task.getRunId();
            ProcessRun run = runMap.get(runId);
            if (run == null) {
                continue;
            }

            // Determine batch number
            String batchNo = null;
            if (run.getBatchId() != null) {
                ProductionBatch batch = batchMap.get(run.getBatchId());
                if (batch != null) {
                    batchNo = batch.getBatchNo();
                }
            }

            // Determine station name
            String stationName = null;
            if (run.getStationId() != null) {
                Workstation ws = stationMap.get(run.getStationId());
                if (ws != null) {
                    stationName = ws.getStationName();
                }
            }

            // Get defect records for this inspection (from pre-loaded map)
            List<DefectRecord> defects = defectsByInspection.getOrDefault(task.getInspectionId(), List.of());
            if (defects.isEmpty()) {
                String defectTypeName = "N/A";
                double confidence = task.getConfidence() != null ? task.getConfidence().doubleValue() : 0.0;
                String status = task.getResultStatus() != null ? task.getResultStatus().toLowerCase() : "pass";

                allRecords.add(new ExportRecord(
                        task.getInspectionId().toString(),
                        formatInstant(task.getInspectedAt()),
                        batchNo != null ? batchNo : "",
                        stationName != null ? stationName : "",
                        defectTypeName,
                        confidence,
                        status
                ));
            } else {
                for (DefectRecord dr : defects) {
                    String defectTypeName = "Unknown";
                    if (dr.getDefectTypeId() != null) {
                        DefectType dt = defectTypeMap.get(dr.getDefectTypeId());
                        if (dt != null) {
                            defectTypeName = dt.getDefectName();
                        }
                    }

                    double confidence = dr.getConfidence() != null ? dr.getConfidence().doubleValue() : 0.0;
                    String status = task.getResultStatus() != null ? task.getResultStatus().toLowerCase() : "pass";

                    allRecords.add(new ExportRecord(
                            dr.getDefectId().toString(),
                            formatInstant(task.getInspectedAt()),
                            batchNo != null ? batchNo : "",
                            stationName != null ? stationName : "",
                            defectTypeName,
                            confidence,
                            status
                    ));
                }
            }
        }

        // Sort by date descending
        allRecords.sort(Comparator.comparing(ExportRecord::date, Comparator.reverseOrder()));

        // Apply filters
        List<ExportRecord> filtered = allRecords.stream()
                .filter(r -> matchBatchId(r, params.batchId()))
                .filter(r -> matchStation(r, params.station()))
                .filter(r -> matchStatus(r, params.status()))
                .filter(r -> matchDateRange(r, params.dateRange()))
                .toList();

        int total = filtered.size();

        // Apply pagination
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<ExportRecord> paged = filtered.subList(from, to);

        return new ExportPageResult(paged, total);
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
        ExportPageResult result = getRecords(params);
        // Generate a simple CSV-like content as a placeholder for Excel
        StringBuilder sb = new StringBuilder();
        sb.append("ID,Date,BatchId,Station,DefectType,Confidence,Status\n");
        for (ExportRecord r : result.list()) {
            sb.append(csvEscape(r.id())).append(",")
              .append(csvEscape(r.date())).append(",")
              .append(csvEscape(r.batchId())).append(",")
              .append(csvEscape(r.station())).append(",")
              .append(csvEscape(r.defectType())).append(",")
              .append(r.confidence()).append(",")
              .append(csvEscape(r.status())).append("\n");
        }
        return sb.toString().getBytes();
    }

    public byte[] generatePdfBytes(ExportSearchParams params) {
        ExportPageResult result = getRecords(params);
        // Generate a simple text content as a placeholder for PDF
        StringBuilder sb = new StringBuilder();
        sb.append("Assessment Report\n");
        sb.append("=================\n\n");
        sb.append("Total records: ").append(result.total()).append("\n\n");
        for (ExportRecord r : result.list()) {
            sb.append("ID: ").append(r.id())
              .append(" | Date: ").append(r.date())
              .append(" | Batch: ").append(r.batchId())
              .append(" | Station: ").append(r.station())
              .append(" | Defect: ").append(r.defectType())
              .append(" | Confidence: ").append(r.confidence())
              .append(" | Status: ").append(r.status())
              .append("\n");
        }
        return sb.toString().getBytes();
    }

    // ==================== Filter helpers ====================

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
            // Parse the record date (format: "yyyy-MM-dd HH:mm:ss")
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

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

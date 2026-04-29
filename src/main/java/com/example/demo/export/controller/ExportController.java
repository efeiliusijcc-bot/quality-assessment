package com.example.demo.export.controller;

import com.example.demo.common.api.ApiResponse;
import com.example.demo.export.dto.ExportDtos.ExportFileResponse;
import com.example.demo.export.dto.ExportDtos.ExportPageResult;
import com.example.demo.export.dto.ExportDtos.ExportSearchParams;
import com.example.demo.export.service.ExportService;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/records")
    public ApiResponse<ExportPageResult> getRecords(
            @RequestParam(required = false) String batchId,
            @RequestParam(required = false) String station,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) List<String> dateRange,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        ExportSearchParams params = new ExportSearchParams(batchId, station, status, dateRange, page, pageSize);
        return ApiResponse.success(exportService.getRecords(params));
    }

    @PostMapping("/excel")
    public ApiResponse<ExportFileResponse> exportExcel(
            @RequestBody(required = false) ExportSearchParams params) {
        if (params == null) {
            params = new ExportSearchParams(null, null, null, null, 1, 10);
        }
        return ApiResponse.success(exportService.exportExcel(params));
    }

    @PostMapping("/pdf")
    public ApiResponse<ExportFileResponse> exportPdf(
            @RequestBody(required = false) ExportSearchParams params) {
        if (params == null) {
            params = new ExportSearchParams(null, null, null, null, 1, 10);
        }
        return ApiResponse.success(exportService.exportPdf(params));
    }

    @PostMapping("/excel/download")
    public ResponseEntity<byte[]> downloadExcel(
            @RequestBody(required = false) ExportSearchParams params) {
        if (params == null) {
            params = new ExportSearchParams(null, null, null, null, 1, 10);
        }
        byte[] data = exportService.generateExcelBytes(params);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=assessment.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    @PostMapping("/pdf/download")
    public ResponseEntity<byte[]> downloadPdf(
            @RequestBody(required = false) ExportSearchParams params) {
        if (params == null) {
            params = new ExportSearchParams(null, null, null, null, 1, 10);
        }
        byte[] data = exportService.generatePdfBytes(params);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=assessment-report.pdf")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }
}

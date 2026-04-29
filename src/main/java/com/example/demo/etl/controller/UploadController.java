package com.example.demo.etl.controller;

import com.example.demo.common.api.ApiResponse;
import com.example.demo.etl.dto.EtlDtos.ManualRecordPayload;
import com.example.demo.etl.dto.EtlDtos.ManualRecordResult;
import com.example.demo.etl.dto.EtlDtos.ManufacturingImportSummary;
import com.example.demo.etl.dto.EtlDtos.OnlineUploadPayload;
import com.example.demo.etl.dto.EtlDtos.OnlineUploadResult;
import com.example.demo.etl.dto.EtlDtos.UploadStatisticsResponse;
import com.example.demo.etl.service.EtlService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private final EtlService etlService;

    public UploadController(EtlService etlService) {
        this.etlService = etlService;
    }

    @PostMapping("/online")
    public ApiResponse<OnlineUploadResult> submitOnlineUpload(@Valid @RequestBody OnlineUploadPayload payload) {
        return ApiResponse.success(etlService.submitOnlineUpload(payload));
    }

    @PostMapping("/manual")
    public ApiResponse<ManualRecordResult> submitManualRecord(@Valid @RequestBody ManualRecordPayload payload) {
        return ApiResponse.success(etlService.submitManualRecord(payload));
    }

    @PostMapping("/manufacturing-data/import")
    public ApiResponse<ManufacturingImportSummary> importManufacturingData(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(etlService.importManufacturingData(file));
    }

    @GetMapping("/statistics")
    public ApiResponse<UploadStatisticsResponse> getStatistics() {
        return ApiResponse.success(etlService.getUploadStatistics());
    }
}

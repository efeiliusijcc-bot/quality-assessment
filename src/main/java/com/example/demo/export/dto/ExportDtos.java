package com.example.demo.export.dto;

import java.util.List;

public final class ExportDtos {

    private ExportDtos() {}

    public record ExportSearchParams(
        String batchId,
        String station,
        String status,
        List<String> dateRange,
        int page,
        int pageSize
    ) {}

    public record ExportRecord(
        String id,
        String date,
        String batchId,
        String station,
        String defectType,
        double confidence,
        String status
    ) {}

    public record ExportPageResult(
        List<ExportRecord> list,
        int total
    ) {}

    public record ExportFileResponse(
        String fileName
    ) {}
}

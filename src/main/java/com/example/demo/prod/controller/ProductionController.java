package com.example.demo.prod.controller;

import com.example.demo.common.api.ApiResponse;
import com.example.demo.prod.dto.ProductionDtos.BatchResponse;
import com.example.demo.prod.dto.ProductionDtos.CreateBatchRequest;
import com.example.demo.prod.dto.ProductionDtos.CreateDeviceLogRequest;
import com.example.demo.prod.dto.ProductionDtos.CreateParameterValueRequest;
import com.example.demo.prod.dto.ProductionDtos.CreateProcessRecipeRequest;
import com.example.demo.prod.dto.ProductionDtos.CreateProcessRunRequest;
import com.example.demo.prod.dto.ProductionDtos.CreateProductUnitRequest;
import com.example.demo.prod.dto.ProductionDtos.DeviceLogResponse;
import com.example.demo.prod.dto.ProductionDtos.ParameterValueDetailResponse;
import com.example.demo.prod.dto.ProductionDtos.ProcessRecipeResponse;
import com.example.demo.prod.dto.ProductionDtos.ProcessRunDetailResponse;
import com.example.demo.prod.dto.ProductionDtos.ProductUnitResponse;
import com.example.demo.prod.service.ProductionBatchService;
import com.example.demo.prod.service.ProductionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prod")
public class ProductionController {

    private final ProductionBatchService batchService;
    private final ProductionService productionService;

    public ProductionController(ProductionBatchService batchService, ProductionService productionService) {
        this.batchService = batchService;
        this.productionService = productionService;
    }

    // ---- Batch ----

    @PostMapping("/batches")
    public ApiResponse<BatchResponse> createBatch(@Valid @RequestBody CreateBatchRequest request) {
        return ApiResponse.success(batchService.createBatch(request));
    }

    @GetMapping("/batches")
    public ApiResponse<List<BatchResponse>> listBatches() {
        return ApiResponse.success(batchService.listBatches());
    }

    @GetMapping("/batches/{id}")
    public ApiResponse<BatchResponse> getBatch(@PathVariable UUID id) {
        return ApiResponse.success(batchService.getBatch(id));
    }

    @GetMapping("/batches/by-no/{batchNo}")
    public ApiResponse<BatchResponse> getBatchByNo(@PathVariable String batchNo) {
        return ApiResponse.success(batchService.getBatchByNo(batchNo));
    }

    // ---- Unit ----

    @PostMapping("/units")
    public ApiResponse<ProductUnitResponse> createUnit(@Valid @RequestBody CreateProductUnitRequest request) {
        return ApiResponse.success(productionService.createUnit(request));
    }

    @GetMapping("/units")
    public ApiResponse<List<ProductUnitResponse>> listUnits(@RequestParam(required = false) UUID batchId) {
        if (batchId != null) {
            return ApiResponse.success(productionService.listUnitsByBatch(batchId));
        }
        return ApiResponse.success(List.of());
    }

    @GetMapping("/units/{id}")
    public ApiResponse<ProductUnitResponse> getUnit(@PathVariable UUID id) {
        return ApiResponse.success(productionService.getUnit(id));
    }

    // ---- Recipe ----

    @PostMapping("/recipes")
    public ApiResponse<ProcessRecipeResponse> createRecipe(@Valid @RequestBody CreateProcessRecipeRequest request) {
        return ApiResponse.success(productionService.createRecipe(request));
    }

    @GetMapping("/recipes")
    public ApiResponse<List<ProcessRecipeResponse>> listRecipes(
            @RequestParam(required = false) UUID productTypeId,
            @RequestParam(required = false) UUID stepId) {
        if (productTypeId != null) {
            return ApiResponse.success(productionService.listRecipesByProductType(productTypeId));
        }
        if (stepId != null) {
            return ApiResponse.success(productionService.listRecipesByStep(stepId));
        }
        return ApiResponse.success(productionService.listAllRecipes());
    }

    @GetMapping("/recipes/{id}")
    public ApiResponse<ProcessRecipeResponse> getRecipe(@PathVariable UUID id) {
        return ApiResponse.success(productionService.getRecipe(id));
    }

    // ---- Run ----

    @PostMapping("/runs")
    public ApiResponse<ProcessRunDetailResponse> createRun(@Valid @RequestBody CreateProcessRunRequest request) {
        return ApiResponse.success(productionService.createRun(request));
    }

    @GetMapping("/runs")
    public ApiResponse<List<ProcessRunDetailResponse>> listRuns(
            @RequestParam(required = false) UUID batchId,
            @RequestParam(required = false) UUID unitId) {
        if (unitId != null) {
            return ApiResponse.success(productionService.listRunsByUnit(unitId));
        }
        if (batchId != null) {
            return ApiResponse.success(productionService.listRunsByBatch(batchId));
        }
        return ApiResponse.success(List.of());
    }

    @GetMapping("/runs/{id}")
    public ApiResponse<ProcessRunDetailResponse> getRun(@PathVariable UUID id) {
        return ApiResponse.success(productionService.getRun(id));
    }

    // ---- ParameterValue ----

    @PostMapping("/parameter-values")
    public ApiResponse<ParameterValueDetailResponse> createParameterValue(
            @Valid @RequestBody CreateParameterValueRequest request) {
        return ApiResponse.success(productionService.createParameterValue(request));
    }

    @GetMapping("/parameter-values")
    public ApiResponse<List<ParameterValueDetailResponse>> listParameterValues(@RequestParam UUID runId) {
        return ApiResponse.success(productionService.listParameterValuesByRun(runId));
    }

    // ---- DeviceLog ----

    @PostMapping("/device-logs")
    public ApiResponse<DeviceLogResponse> createDeviceLog(@Valid @RequestBody CreateDeviceLogRequest request) {
        return ApiResponse.success(productionService.createDeviceLog(request));
    }

    @GetMapping("/device-logs")
    public ApiResponse<List<DeviceLogResponse>> listDeviceLogs(
            @RequestParam(required = false) UUID runId,
            @RequestParam(required = false) UUID equipmentId) {
        if (equipmentId != null) {
            return ApiResponse.success(productionService.listDeviceLogsByEquipment(equipmentId));
        }
        if (runId != null) {
            return ApiResponse.success(productionService.listDeviceLogsByRun(runId));
        }
        return ApiResponse.success(List.of());
    }
}

package com.example.demo.core.controller;

import com.example.demo.common.api.ApiResponse;
import com.example.demo.core.dto.CoreDtos.*;
import com.example.demo.core.service.CoreService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/core")
public class CoreController {

    private final CoreService coreService;

    public CoreController(CoreService coreService) {
        this.coreService = coreService;
    }

    // ==================== ProcessStep ====================

    @PostMapping("/process-steps")
    public ApiResponse<ProcessStepResponse> createProcessStep(@Valid @RequestBody CreateProcessStepRequest request) {
        return ApiResponse.success(coreService.createProcessStep(request));
    }

    @GetMapping("/process-steps")
    public ApiResponse<List<ProcessStepResponse>> listProcessSteps() {
        return ApiResponse.success(coreService.listProcessSteps());
    }

    @GetMapping("/process-steps/{id}")
    public ApiResponse<ProcessStepResponse> getProcessStep(@PathVariable UUID id) {
        return ApiResponse.success(coreService.getProcessStep(id));
    }

    // ==================== Workstation ====================

    @PostMapping("/workstations")
    public ApiResponse<WorkstationResponse> createWorkstation(@Valid @RequestBody CreateWorkstationRequest request) {
        return ApiResponse.success(coreService.createWorkstation(request));
    }

    @GetMapping("/workstations")
    public ApiResponse<List<WorkstationResponse>> listWorkstations(@RequestParam(required = false) UUID stepId) {
        if (stepId != null) {
            return ApiResponse.success(coreService.listWorkstationsByStep(stepId));
        }
        return ApiResponse.success(coreService.listWorkstations());
    }

    @GetMapping("/workstations/{id}")
    public ApiResponse<WorkstationResponse> getWorkstation(@PathVariable UUID id) {
        return ApiResponse.success(coreService.getWorkstation(id));
    }

    // ==================== Equipment ====================

    @PostMapping("/equipment")
    public ApiResponse<EquipmentResponse> createEquipment(@Valid @RequestBody CreateEquipmentRequest request) {
        return ApiResponse.success(coreService.createEquipment(request));
    }

    @GetMapping("/equipment")
    public ApiResponse<List<EquipmentResponse>> listEquipment(@RequestParam(required = false) UUID stationId) {
        if (stationId != null) {
            return ApiResponse.success(coreService.listEquipmentByStation(stationId));
        }
        return ApiResponse.success(coreService.listEquipment());
    }

    @GetMapping("/equipment/{id}")
    public ApiResponse<EquipmentResponse> getEquipment(@PathVariable UUID id) {
        return ApiResponse.success(coreService.getEquipment(id));
    }

    // ==================== ProductType ====================

    @PostMapping("/product-types")
    public ApiResponse<ProductTypeResponse> createProductType(@Valid @RequestBody CreateProductTypeRequest request) {
        return ApiResponse.success(coreService.createProductType(request));
    }

    @GetMapping("/product-types")
    public ApiResponse<List<ProductTypeResponse>> listProductTypes() {
        return ApiResponse.success(coreService.listProductTypes());
    }

    @GetMapping("/product-types/{id}")
    public ApiResponse<ProductTypeResponse> getProductType(@PathVariable UUID id) {
        return ApiResponse.success(coreService.getProductType(id));
    }

    // ==================== ParameterDef ====================

    @PostMapping("/parameter-defs")
    public ApiResponse<ParameterDefResponse> createParameterDef(@Valid @RequestBody CreateParameterDefRequest request) {
        return ApiResponse.success(coreService.createParameterDef(request));
    }

    @GetMapping("/parameter-defs")
    public ApiResponse<List<ParameterDefResponse>> listParameterDefs(@RequestParam(required = false) UUID stepId) {
        if (stepId != null) {
            return ApiResponse.success(coreService.listParameterDefsByStep(stepId));
        }
        return ApiResponse.success(coreService.listParameterDefs());
    }

    @GetMapping("/parameter-defs/{id}")
    public ApiResponse<ParameterDefResponse> getParameterDef(@PathVariable UUID id) {
        return ApiResponse.success(coreService.getParameterDef(id));
    }

    // ==================== FileResource ====================

    @GetMapping("/files")
    public ApiResponse<List<FileResourceResponse>> listFiles() {
        return ApiResponse.success(coreService.listFileResources());
    }

    @GetMapping("/files/{id}")
    public ApiResponse<FileResourceResponse> getFile(@PathVariable UUID id) {
        return ApiResponse.success(coreService.getFileResource(id));
    }
}

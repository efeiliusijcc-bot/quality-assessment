package com.example.demo.prod.service;

import com.example.demo.common.exception.BusinessException;
import com.example.demo.prod.domain.DeviceLog;
import com.example.demo.prod.domain.ParameterValue;
import com.example.demo.prod.domain.ProcessRecipe;
import com.example.demo.prod.domain.ProcessRun;
import com.example.demo.prod.domain.ProductUnit;
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
import com.example.demo.prod.repository.DeviceLogRepository;
import com.example.demo.prod.repository.ParameterValueRepository;
import com.example.demo.prod.repository.ProcessRecipeRepository;
import com.example.demo.prod.repository.ProcessRunRepository;
import com.example.demo.prod.repository.ProductUnitRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProductionService {

    private final ProductUnitRepository unitRepository;
    private final ProcessRecipeRepository recipeRepository;
    private final ProcessRunRepository runRepository;
    private final ParameterValueRepository paramValueRepository;
    private final DeviceLogRepository deviceLogRepository;

    public ProductionService(
            ProductUnitRepository unitRepository,
            ProcessRecipeRepository recipeRepository,
            ProcessRunRepository runRepository,
            ParameterValueRepository paramValueRepository,
            DeviceLogRepository deviceLogRepository) {
        this.unitRepository = unitRepository;
        this.recipeRepository = recipeRepository;
        this.runRepository = runRepository;
        this.paramValueRepository = paramValueRepository;
        this.deviceLogRepository = deviceLogRepository;
    }

    // ---- ProductUnit ----

    public ProductUnitResponse createUnit(CreateProductUnitRequest request) {
        if (unitRepository.existsByBatchIdAndSerialNo(request.batchId(), request.serialNo())) {
            throw new BusinessException(400, "serialNo already exists in this batch");
        }
        ProductUnit unit = new ProductUnit(request.batchId(), request.serialNo());
        unitRepository.save(unit);
        return toUnitResponse(unit);
    }

    public ProductUnitResponse getUnit(UUID unitId) {
        return toUnitResponse(requireUnit(unitId));
    }

    public List<ProductUnitResponse> listUnitsByBatch(UUID batchId) {
        return unitRepository.findByBatchId(batchId).stream().map(this::toUnitResponse).toList();
    }

    private ProductUnit requireUnit(UUID unitId) {
        return unitRepository.findById(unitId)
            .orElseThrow(() -> new BusinessException(404, "unit not found"));
    }

    private ProductUnitResponse toUnitResponse(ProductUnit u) {
        return new ProductUnitResponse(
            u.getUnitId(), u.getBatchId(), u.getSerialNo(),
            u.getCurrentStepId(), u.getUnitStatus()
        );
    }

    // ---- ProcessRecipe ----

    public ProcessRecipeResponse createRecipe(CreateProcessRecipeRequest request) {
        if (recipeRepository.existsByRecipeCode(request.recipeCode())) {
            throw new BusinessException(400, "recipeCode already exists");
        }
        ProcessRecipe recipe = new ProcessRecipe(request.stepId(), request.recipeCode(), request.recipeName());
        recipe.setProductTypeId(request.productTypeId());
        recipeRepository.save(recipe);
        return toRecipeResponse(recipe);
    }

    public ProcessRecipeResponse getRecipe(UUID recipeId) {
        return toRecipeResponse(requireRecipe(recipeId));
    }

    public List<ProcessRecipeResponse> listAllRecipes() {
        return recipeRepository.findAll().stream().map(this::toRecipeResponse).toList();
    }

    public List<ProcessRecipeResponse> listRecipesByProductType(UUID productTypeId) {
        return recipeRepository.findByProductTypeId(productTypeId).stream()
            .map(this::toRecipeResponse).toList();
    }

    public List<ProcessRecipeResponse> listRecipesByStep(UUID stepId) {
        return recipeRepository.findByStepId(stepId).stream().map(this::toRecipeResponse).toList();
    }

    private ProcessRecipe requireRecipe(UUID recipeId) {
        return recipeRepository.findById(recipeId)
            .orElseThrow(() -> new BusinessException(404, "recipe not found"));
    }

    private ProcessRecipeResponse toRecipeResponse(ProcessRecipe r) {
        return new ProcessRecipeResponse(
            r.getRecipeId(), r.getRecipeCode(), r.getRecipeName(),
            r.getProductTypeId(), r.getStepId(), r.getVersionNo(), r.getIsActive()
        );
    }

    // ---- ProcessRun ----

    public ProcessRunDetailResponse createRun(CreateProcessRunRequest request) {
        ProcessRun run = new ProcessRun(request.batchId(), request.stepId());
        run.setUnitId(request.unitId());
        run.setStationId(request.stationId());
        run.setEquipmentId(request.equipmentId());
        run.setRecipeId(request.recipeId());
        runRepository.save(run);
        return toRunResponse(run);
    }

    public ProcessRunDetailResponse getRun(UUID runId) {
        return toRunResponse(requireRun(runId));
    }

    public List<ProcessRunDetailResponse> listRunsByBatch(UUID batchId) {
        return runRepository.findByBatchIdOrderByCreatedAtAsc(batchId).stream()
            .map(this::toRunResponse).toList();
    }

    public List<ProcessRunDetailResponse> listRunsByUnit(UUID unitId) {
        return runRepository.findByUnitIdOrderByCreatedAtAsc(unitId).stream()
            .map(this::toRunResponse).toList();
    }

    private ProcessRun requireRun(UUID runId) {
        return runRepository.findById(runId)
            .orElseThrow(() -> new BusinessException(404, "run not found"));
    }

    private ProcessRunDetailResponse toRunResponse(ProcessRun r) {
        return new ProcessRunDetailResponse(
            r.getRunId(), r.getBatchId(), r.getUnitId(), r.getStepId(),
            r.getStationId(), r.getEquipmentId(), r.getRecipeId(),
            r.getRunNo(), r.getRunStatus(),
            r.getStartTime() != null ? r.getStartTime().toString() : null,
            r.getEndTime() != null ? r.getEndTime().toString() : null
        );
    }

    // ---- ParameterValue ----

    public ParameterValueDetailResponse createParameterValue(CreateParameterValueRequest request) {
        BigDecimal num = request.valueNum() != null ? BigDecimal.valueOf(request.valueNum()) : BigDecimal.ZERO;
        ParameterValue pv = new ParameterValue(request.runId(), request.paramId(), num);
        pv.setValueText(request.valueText());
        paramValueRepository.save(pv);
        return toParamValueResponse(pv);
    }

    public List<ParameterValueDetailResponse> listParameterValuesByRun(UUID runId) {
        return paramValueRepository.findByRunIdOrderByMeasuredAtAsc(runId).stream()
            .map(this::toParamValueResponse).toList();
    }

    private ParameterValueDetailResponse toParamValueResponse(ParameterValue pv) {
        return new ParameterValueDetailResponse(
            pv.getValueId(), pv.getRunId(), pv.getParamId(),
            pv.getValueNum(), pv.getValueText(), pv.getQualityFlag(),
            pv.getMeasuredAt() != null ? pv.getMeasuredAt().toString() : null
        );
    }

    // ---- DeviceLog ----

    public DeviceLogResponse createDeviceLog(CreateDeviceLogRequest request) {
        DeviceLog log = new DeviceLog(request.equipmentId(), request.logContent());
        log.setRunId(request.runId());
        log.setLogLevel(request.logLevel());
        deviceLogRepository.save(log);
        return toDeviceLogResponse(log);
    }

    public List<DeviceLogResponse> listDeviceLogsByRun(UUID runId) {
        return deviceLogRepository.findAll().stream()
            .filter(l -> runId.equals(l.getRunId()))
            .map(this::toDeviceLogResponse).toList();
    }

    public List<DeviceLogResponse> listDeviceLogsByEquipment(UUID equipmentId) {
        return deviceLogRepository.findByEquipmentIdOrderByLogTimeDesc(equipmentId).stream()
            .map(this::toDeviceLogResponse).toList();
    }

    private DeviceLogResponse toDeviceLogResponse(DeviceLog l) {
        return new DeviceLogResponse(
            l.getLogId(), l.getRunId(), l.getEquipmentId(),
            l.getLogTime() != null ? l.getLogTime().toString() : null,
            l.getLogLevel(), l.getAlarmCode(), l.getAlarmName(), l.getLogContent()
        );
    }
}

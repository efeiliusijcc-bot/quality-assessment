package com.example.demo.core.service;

import com.example.demo.common.exception.BusinessException;
import com.example.demo.core.domain.*;
import com.example.demo.core.dto.CoreDtos.*;
import com.example.demo.core.repository.*;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CoreService {

    private final ProcessStepRepository processStepRepository;
    private final WorkstationRepository workstationRepository;
    private final EquipmentRepository equipmentRepository;
    private final ProductTypeRepository productTypeRepository;
    private final ParameterDefRepository parameterDefRepository;
    private final FileResourceRepository fileResourceRepository;

    public CoreService(
            ProcessStepRepository processStepRepository,
            WorkstationRepository workstationRepository,
            EquipmentRepository equipmentRepository,
            ProductTypeRepository productTypeRepository,
            ParameterDefRepository parameterDefRepository,
            FileResourceRepository fileResourceRepository) {
        this.processStepRepository = processStepRepository;
        this.workstationRepository = workstationRepository;
        this.equipmentRepository = equipmentRepository;
        this.productTypeRepository = productTypeRepository;
        this.parameterDefRepository = parameterDefRepository;
        this.fileResourceRepository = fileResourceRepository;
    }

    // ==================== ProcessStep ====================

    public ProcessStepResponse createProcessStep(CreateProcessStepRequest request) {
        if (processStepRepository.findByStepCode(request.stepCode()).isPresent()) {
            throw new BusinessException(400, "stepCode already exists");
        }
        ProcessStep entity = new ProcessStep(request.stepCode(), request.stepName(), request.stepOrder(), request.isInspection());
        entity.setDescription(request.description());
        processStepRepository.save(entity);
        return toProcessStepResponse(entity);
    }

    public ProcessStepResponse getProcessStep(UUID id) {
        return toProcessStepResponse(requireProcessStep(id));
    }

    public List<ProcessStepResponse> listProcessSteps() {
        return processStepRepository.findAll().stream().map(this::toProcessStepResponse).toList();
    }

    private ProcessStep requireProcessStep(UUID id) {
        return processStepRepository.findById(id)
            .orElseThrow(() -> new BusinessException(404, "processStep not found"));
    }

    private ProcessStepResponse toProcessStepResponse(ProcessStep e) {
        return new ProcessStepResponse(
            e.getStepId(), e.getStepCode(), e.getStepName(),
            e.getStepOrder(), e.getIsInspection(), e.getDescription()
        );
    }

    // ==================== Workstation ====================

    public WorkstationResponse createWorkstation(CreateWorkstationRequest request) {
        if (workstationRepository.findByStationCode(request.stationCode()).isPresent()) {
            throw new BusinessException(400, "stationCode already exists");
        }
        Workstation entity = new Workstation(request.stepId(), request.stationCode(), request.stationName());
        entity.setLocation(request.location());
        workstationRepository.save(entity);
        return toWorkstationResponse(entity);
    }

    public WorkstationResponse getWorkstation(UUID id) {
        return toWorkstationResponse(requireWorkstation(id));
    }

    public List<WorkstationResponse> listWorkstations() {
        return workstationRepository.findAll().stream().map(this::toWorkstationResponse).toList();
    }

    public List<WorkstationResponse> listWorkstationsByStep(UUID stepId) {
        return workstationRepository.findByStepId(stepId).stream().map(this::toWorkstationResponse).toList();
    }

    private Workstation requireWorkstation(UUID id) {
        return workstationRepository.findById(id)
            .orElseThrow(() -> new BusinessException(404, "workstation not found"));
    }

    private WorkstationResponse toWorkstationResponse(Workstation e) {
        String stepCode = processStepRepository.findById(e.getStepId())
            .map(ProcessStep::getStepCode).orElse(null);
        return new WorkstationResponse(
            e.getStationId(), e.getStepId(), stepCode,
            e.getStationCode(), e.getStationName(), e.getLocation(), e.getStatus()
        );
    }

    // ==================== Equipment ====================

    public EquipmentResponse createEquipment(CreateEquipmentRequest request) {
        if (equipmentRepository.findByEquipmentCode(request.equipmentCode()).isPresent()) {
            throw new BusinessException(400, "equipmentCode already exists");
        }
        Equipment entity = new Equipment(request.equipmentCode(), request.equipmentName());
        entity.setStationId(request.stationId());
        entity.setEquipmentType(request.equipmentType());
        equipmentRepository.save(entity);
        return toEquipmentResponse(entity);
    }

    public EquipmentResponse getEquipment(UUID id) {
        return toEquipmentResponse(requireEquipment(id));
    }

    public List<EquipmentResponse> listEquipment() {
        return equipmentRepository.findAll().stream().map(this::toEquipmentResponse).toList();
    }

    public List<EquipmentResponse> listEquipmentByStation(UUID stationId) {
        return equipmentRepository.findByStationId(stationId).stream().map(this::toEquipmentResponse).toList();
    }

    private Equipment requireEquipment(UUID id) {
        return equipmentRepository.findById(id)
            .orElseThrow(() -> new BusinessException(404, "equipment not found"));
    }

    private EquipmentResponse toEquipmentResponse(Equipment e) {
        return new EquipmentResponse(
            e.getEquipmentId(), e.getStationId(), e.getEquipmentCode(), e.getEquipmentName(),
            e.getEquipmentType(), e.getManufacturer(), e.getModelNo(), e.getStatus(),
            e.getInstalledAt() != null ? e.getInstalledAt().toString() : null
        );
    }

    // ==================== ProductType ====================

    public ProductTypeResponse createProductType(CreateProductTypeRequest request) {
        if (productTypeRepository.findByProductCode(request.productCode()).isPresent()) {
            throw new BusinessException(400, "productCode already exists");
        }
        ProductType entity = new ProductType(request.productCode(), request.productName());
        if (request.materialSystem() != null) {
            entity.setMaterialSystem(request.materialSystem());
        }
        productTypeRepository.save(entity);
        return toProductTypeResponse(entity);
    }

    public ProductTypeResponse getProductType(UUID id) {
        return toProductTypeResponse(requireProductType(id));
    }

    public List<ProductTypeResponse> listProductTypes() {
        return productTypeRepository.findAll().stream().map(this::toProductTypeResponse).toList();
    }

    private ProductType requireProductType(UUID id) {
        return productTypeRepository.findById(id)
            .orElseThrow(() -> new BusinessException(404, "productType not found"));
    }

    private ProductTypeResponse toProductTypeResponse(ProductType e) {
        return new ProductTypeResponse(
            e.getProductTypeId(), e.getProductCode(), e.getProductName(),
            e.getMaterialSystem(), e.getSpecification()
        );
    }

    // ==================== ParameterDef ====================

    public ParameterDefResponse createParameterDef(CreateParameterDefRequest request) {
        if (parameterDefRepository.findByStepIdAndParamCodeAndParamCategory(
                request.stepId(), request.paramCode(), request.paramCategory() != null ? request.paramCategory() : "").isPresent()) {
            throw new BusinessException(400, "paramCode already exists");
        }
        ParameterDef entity = new ParameterDef(request.paramCode(), request.paramName(), request.paramCategory(), request.dataType());
        entity.setStepId(request.stepId());
        entity.setUnit(request.unit());
        parameterDefRepository.save(entity);
        return toParameterDefResponse(entity);
    }

    public ParameterDefResponse getParameterDef(UUID id) {
        return toParameterDefResponse(requireParameterDef(id));
    }

    public List<ParameterDefResponse> listParameterDefs() {
        return parameterDefRepository.findAll().stream().map(this::toParameterDefResponse).toList();
    }

    public List<ParameterDefResponse> listParameterDefsByStep(UUID stepId) {
        return parameterDefRepository.findByStepId(stepId).stream().map(this::toParameterDefResponse).toList();
    }

    private ParameterDef requireParameterDef(UUID id) {
        return parameterDefRepository.findById(id)
            .orElseThrow(() -> new BusinessException(404, "parameterDef not found"));
    }

    private ParameterDefResponse toParameterDefResponse(ParameterDef e) {
        return new ParameterDefResponse(
            e.getParamId(), e.getStepId(), e.getParamCode(), e.getParamName(),
            e.getParamCategory(), e.getDataType(), e.getUnit(),
            e.getLowerLimit(), e.getUpperLimit(), e.getStandardValue(),
            e.getRequiredFlag(), e.getDescription()
        );
    }

    // ==================== FileResource ====================

    public FileResourceResponse getFileResource(UUID id) {
        return toFileResourceResponse(requireFileResource(id));
    }

    public List<FileResourceResponse> listFileResources() {
        return fileResourceRepository.findAll().stream().map(this::toFileResourceResponse).toList();
    }

    private FileResource requireFileResource(UUID id) {
        return fileResourceRepository.findById(id)
            .orElseThrow(() -> new BusinessException(404, "fileResource not found"));
    }

    private FileResourceResponse toFileResourceResponse(FileResource e) {
        return new FileResourceResponse(
            e.getFileId(), e.getFileType(), e.getFileName(), e.getFilePath(),
            e.getMimeType(), e.getFileSize(), e.getSha256(), e.getUploadedBy(),
            e.getUploadedAt() != null ? e.getUploadedAt().toString() : null
        );
    }
}

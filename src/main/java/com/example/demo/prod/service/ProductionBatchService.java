package com.example.demo.prod.service;

import com.example.demo.common.exception.BusinessException;
import com.example.demo.core.repository.ProductTypeRepository;
import com.example.demo.prod.domain.ProductionBatch;
import com.example.demo.prod.dto.ProductionDtos.BatchResponse;
import com.example.demo.prod.dto.ProductionDtos.CreateBatchRequest;
import com.example.demo.prod.repository.ProductionBatchRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProductionBatchService {

    private final ProductionBatchRepository batchRepository;
    private final ProductTypeRepository productTypeRepository;

    public ProductionBatchService(ProductionBatchRepository batchRepository, ProductTypeRepository productTypeRepository) {
        this.batchRepository = batchRepository;
        this.productTypeRepository = productTypeRepository;
    }

    public BatchResponse createBatch(CreateBatchRequest request) {
        if (batchRepository.findByBatchNo(request.batchNo()).isPresent()) {
            throw new BusinessException(400, "batchNo already exists");
        }
        ProductionBatch batch = new ProductionBatch(request.batchNo(), request.productTypeId());
        batch.setPlanQty(request.planQty());
        batchRepository.save(batch);
        return toResponse(batch);
    }

    public BatchResponse getBatch(UUID batchId) {
        return toResponse(requireBatch(batchId));
    }

    public BatchResponse getBatchByNo(String batchNo) {
        return batchRepository.findByBatchNo(batchNo)
            .map(this::toResponse)
            .orElseThrow(() -> new BusinessException(404, "batch not found"));
    }

    public List<BatchResponse> listBatches() {
        return batchRepository.findAll().stream().map(this::toResponse).toList();
    }

    public List<BatchResponse> listBatchesByProductType(UUID productTypeId) {
        return batchRepository.findByProductTypeId(productTypeId).stream()
            .map(this::toResponse)
            .toList();
    }

    private ProductionBatch requireBatch(UUID batchId) {
        return batchRepository.findById(batchId)
            .orElseThrow(() -> new BusinessException(404, "batch not found"));
    }

    private BatchResponse toResponse(ProductionBatch b) {
        return new BatchResponse(
            b.getBatchId(), b.getBatchNo(), null,
            b.getPlanQty(), b.getActualQty(), b.getBatchStatus(),
            b.getStartTime() != null ? b.getStartTime().toString() : null,
            b.getEndTime() != null ? b.getEndTime().toString() : null
        );
    }
}

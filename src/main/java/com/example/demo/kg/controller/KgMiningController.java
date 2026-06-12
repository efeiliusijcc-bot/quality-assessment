package com.example.demo.kg.controller;

import com.example.demo.common.api.ApiResponse;
import com.example.demo.kg.apriori.AprioriMiningService;
import com.example.demo.kg.nlp.DomainLexiconService;
import com.example.demo.kg.nlp.JiebaEntityExtractionService;
import com.example.demo.kg.service.Neo4jRelationshipRepairService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/graph")
public class KgMiningController {

    private final JiebaEntityExtractionService entityExtractionService;
    private final DomainLexiconService domainLexiconService;
    private final AprioriMiningService aprioriMiningService;
    private final Neo4jRelationshipRepairService neo4jRelationshipRepairService;

    public KgMiningController(JiebaEntityExtractionService entityExtractionService,
                              DomainLexiconService domainLexiconService,
                              AprioriMiningService aprioriMiningService,
                              Neo4jRelationshipRepairService neo4jRelationshipRepairService) {
        this.entityExtractionService = entityExtractionService;
        this.domainLexiconService = domainLexiconService;
        this.aprioriMiningService = aprioriMiningService;
        this.neo4jRelationshipRepairService = neo4jRelationshipRepairService;
    }

    @PostMapping("/nlp/extract")
    public ApiResponse<JiebaEntityExtractionService.ExtractionResult> extractEntities(@Valid @RequestBody ExtractRequest request) {
        return ApiResponse.success(entityExtractionService.extract(request.text()));
    }

    @PostMapping("/nlp/lexicon/refresh")
    public ApiResponse<Map<String, Integer>> refreshLexicon() {
        return ApiResponse.success(Map.of("termCount", domainLexiconService.refresh()));
    }

    @GetMapping("/apriori/mine")
    public ApiResponse<AprioriMiningService.AprioriMiningResult> mineApriori(
            @RequestParam(required = false) UUID batchId,
            @RequestParam(defaultValue = "0.08") double minSupport,
            @RequestParam(defaultValue = "0.55") double minConfidence,
            @RequestParam(defaultValue = "3") int maxItemsetSize) {
        return ApiResponse.success(aprioriMiningService.mine(batchId, minSupport, minConfidence, maxItemsetSize));
    }

    @PostMapping("/apriori/mine-and-save")
    public ApiResponse<AprioriMiningService.PersistResult> mineAndSaveApriori(@RequestBody AprioriPersistRequest request) {
        return ApiResponse.success(aprioriMiningService.mineAndPersist(
                request.batchId(), request.graphVersionId(),
                request.minSupport() == null ? 0.08 : request.minSupport(),
                request.minConfidence() == null ? 0.55 : request.minConfidence(),
                request.maxItemsetSize() == null ? 3 : request.maxItemsetSize()
        ));
    }

    @PostMapping("/neo4j/repair-directions")
    public ApiResponse<Map<String, Integer>> repairNeo4jDirections() {
        return ApiResponse.success(neo4jRelationshipRepairService.repairDirections());
    }

    public record ExtractRequest(@NotBlank String text) {}
    public record AprioriPersistRequest(UUID batchId, UUID graphVersionId, Double minSupport, Double minConfidence, Integer maxItemsetSize) {}
}

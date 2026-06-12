package com.example.demo.kg.nlp;

import com.example.demo.core.domain.Equipment;
import com.example.demo.core.domain.ParameterDef;
import com.example.demo.core.domain.ProcessStep;
import com.example.demo.core.domain.Workstation;
import com.example.demo.core.repository.EquipmentRepository;
import com.example.demo.core.repository.ParameterDefRepository;
import com.example.demo.core.repository.ProcessStepRepository;
import com.example.demo.core.repository.WorkstationRepository;
import com.example.demo.qc.domain.DefectType;
import com.example.demo.qc.domain.QualityMetricDef;
import com.example.demo.qc.repository.DefectTypeRepository;
import com.example.demo.qc.repository.QualityMetricDefRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds a domain lexicon from three sources:
 * 1) static resource src/main/resources/kg/domain-lexicon.json;
 * 2) optional table kg.domain_term;
 * 3) live business dictionaries in core/qc schemas.
 */
@Service
public class DomainLexiconService {

    private static final Logger log = LoggerFactory.getLogger(DomainLexiconService.class);

    private final ProcessStepRepository processStepRepository;
    private final ParameterDefRepository parameterDefRepository;
    private final QualityMetricDefRepository qualityMetricDefRepository;
    private final DefectTypeRepository defectTypeRepository;
    private final EquipmentRepository equipmentRepository;
    private final WorkstationRepository workstationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private volatile Map<String, DomainTerm> termIndex = Map.of();
    private volatile List<DomainTerm> termsByLength = List.of();

    public DomainLexiconService(ProcessStepRepository processStepRepository,
                                ParameterDefRepository parameterDefRepository,
                                QualityMetricDefRepository qualityMetricDefRepository,
                                DefectTypeRepository defectTypeRepository,
                                EquipmentRepository equipmentRepository,
                                WorkstationRepository workstationRepository,
                                JdbcTemplate jdbcTemplate,
                                ObjectMapper objectMapper) {
        this.processStepRepository = processStepRepository;
        this.parameterDefRepository = parameterDefRepository;
        this.qualityMetricDefRepository = qualityMetricDefRepository;
        this.defectTypeRepository = defectTypeRepository;
        this.equipmentRepository = equipmentRepository;
        this.workstationRepository = workstationRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    public synchronized int refresh() {
        Map<String, DomainTerm> index = new LinkedHashMap<>();
        loadStaticResource(index);
        loadOptionalDbLexicon(index);
        loadBusinessDictionaries(index);
        this.termIndex = Map.copyOf(index);
        this.termsByLength = index.values().stream()
                .sorted(Comparator.comparingInt((DomainTerm t) -> t.term().length()).reversed())
                .toList();
        log.info("domain lexicon refreshed: {} terms", this.termIndex.size());
        return this.termIndex.size();
    }

    public List<DomainTerm> allTerms() {
        return termsByLength;
    }

    public Optional<DomainTerm> findExact(String token) {
        if (token == null) return Optional.empty();
        return Optional.ofNullable(termIndex.get(normalize(token)));
    }

    public List<DomainTerm> findContains(String text) {
        if (text == null || text.isBlank()) return List.of();
        String normalizedText = normalize(text);
        List<DomainTerm> matches = new ArrayList<>();
        for (DomainTerm term : termsByLength) {
            if (normalizedText.contains(normalize(term.term()))) {
                matches.add(term);
            }
        }
        return matches;
    }

    public String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private void put(Map<String, DomainTerm> index, DomainTerm term) {
        if (term == null || term.term() == null || term.term().isBlank()) return;
        index.putIfAbsent(normalize(term.term()), term);
        for (String alias : term.aliases()) {
            if (alias != null && !alias.isBlank()) {
                index.putIfAbsent(normalize(alias), new DomainTerm(alias, term.type(), term.refId(), term.refSchema(), term.refTable(), term.code(), List.of(term.term()), term.source()));
            }
        }
    }

    private void loadStaticResource(Map<String, DomainTerm> index) {
        try {
            ClassPathResource resource = new ClassPathResource("kg/domain-lexicon.json");
            if (!resource.exists()) return;
            try (InputStream in = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(in);
                JsonNode termsNode = root.isArray() ? root : root.get("terms");
                if (termsNode == null || !termsNode.isArray()) return;
                List<Map<String, Object>> rows = objectMapper.convertValue(termsNode, new TypeReference<>() {});
                for (Map<String, Object> row : rows) {
                    String term = str(row.get("term"), row.get("name"));
                    String type = str(row.get("type"), row.get("entityType"));
                    String code = str(row.get("code"));
                    @SuppressWarnings("unchecked")
                    List<String> aliases = row.get("aliases") instanceof Collection<?> c
                            ? c.stream().map(Object::toString).toList()
                            : List.of();
                    put(index, new DomainTerm(term, type, null, null, null, code, aliases, "RESOURCE"));
                }
            }
        } catch (Exception e) {
            log.warn("failed to load static domain lexicon: {}", e.getMessage());
        }
    }

    private void loadOptionalDbLexicon(Map<String, DomainTerm> index) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT term_name, term_type, aliases, source
                    FROM kg.domain_term
                    WHERE enabled = true
                    """);
            for (Map<String, Object> row : rows) {
                String term = str(row.get("term_name"));
                String type = str(row.get("term_type"));
                List<String> aliases = parseAliases(row.get("aliases"));
                put(index, new DomainTerm(term, type, null, null, null, null, aliases, str(row.get("source"), "DB")));
            }
        } catch (Exception ignored) {
            // kg.domain_term is optional. The system can still build lexicon from resource and business dictionaries.
        }
    }

    private void loadBusinessDictionaries(Map<String, DomainTerm> index) {
        for (ProcessStep s : safe(processStepRepository.findAll())) {
            put(index, new DomainTerm(s.getStepName(), "PROCESS_STEP", s.getStepId(), "core", "process_step", s.getStepCode(), List.of(s.getStepCode()), "DB:core.process_step"));
        }
        for (ParameterDef p : safe(parameterDefRepository.findAll())) {
            put(index, new DomainTerm(p.getParamName(), "PARAMETER", p.getParamId(), "core", "parameter_def", p.getParamCode(), List.of(p.getParamCode()), "DB:core.parameter_def"));
        }
        for (QualityMetricDef m : safe(qualityMetricDefRepository.findAll())) {
            put(index, new DomainTerm(m.getMetricName(), "QUALITY_METRIC", m.getMetricId(), "qc", "quality_metric_def", m.getMetricCode(), List.of(m.getMetricCode()), "DB:qc.quality_metric_def"));
        }
        for (DefectType d : safe(defectTypeRepository.findAll())) {
            put(index, new DomainTerm(d.getDefectName(), "DEFECT_TYPE", d.getDefectTypeId(), "qc", "defect_type", d.getDefectCode(), List.of(d.getDefectCode()), "DB:qc.defect_type"));
        }
        for (Equipment e : safe(equipmentRepository.findAll())) {
            put(index, new DomainTerm(e.getEquipmentName(), "EQUIPMENT", e.getEquipmentId(), "core", "equipment", e.getEquipmentCode(), List.of(e.getEquipmentCode()), "DB:core.equipment"));
        }
        for (Workstation w : safe(workstationRepository.findAll())) {
            put(index, new DomainTerm(w.getStationName(), "WORKSTATION", w.getStationId(), "core", "workstation", w.getStationCode(), List.of(w.getStationCode()), "DB:core.workstation"));
        }
    }

    private <T> List<T> safe(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    private List<String> parseAliases(Object value) {
        if (value == null) return List.of();
        if (value instanceof String[] arr) return List.of(arr);
        String s = value.toString();
        s = s.replace("{", "").replace("}", "");
        if (s.isBlank()) return List.of();
        List<String> list = new ArrayList<>();
        for (String part : s.split(",")) {
            String p = part.trim();
            if (!p.isBlank()) list.add(p);
        }
        return list;
    }

    private String str(Object... values) {
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) return value.toString();
        }
        return null;
    }
}

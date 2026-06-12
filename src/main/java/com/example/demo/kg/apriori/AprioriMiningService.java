package com.example.demo.kg.apriori;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Standard Apriori frequent itemset mining for process-parameter-defect-quality co-occurrence.
 *
 * Transaction granularity: one process_run.
 * Items are generated from:
 * - ProcessStep of the run;
 * - ParameterDef + value state HIGH/LOW/NORMAL/MISSING;
 * - DefectType found in the run;
 * - QualityMetric abnormal/pass state in the run.
 */
@Service
public class AprioriMiningService {

    private static final Logger log = LoggerFactory.getLogger(AprioriMiningService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AprioriMiningService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public AprioriMiningResult mine(UUID batchId, double minSupport, double minConfidence, int maxItemsetSize) {
        Map<String, Set<AprioriItem>> transactions = loadTransactions(batchId);
        return mineFromTransactions(new ArrayList<>(transactions.values()), minSupport, minConfidence, Math.max(2, maxItemsetSize));
    }

    public AprioriMiningResult mineFromTransactions(List<Set<AprioriItem>> transactions,
                                                    double minSupport,
                                                    double minConfidence,
                                                    int maxItemsetSize) {
        List<Set<AprioriItem>> tx = transactions.stream().filter(s -> s != null && !s.isEmpty()).toList();
        int n = tx.size();
        if (n == 0) {
            return new AprioriMiningResult(0, List.of(), List.of());
        }

        Map<Set<AprioriItem>, Integer> supportCounts = new LinkedHashMap<>();
        List<Set<AprioriItem>> frequentAll = new ArrayList<>();

        Map<AprioriItem, Integer> itemCounts = new LinkedHashMap<>();
        for (Set<AprioriItem> t : tx) {
            for (AprioriItem item : t) {
                itemCounts.merge(item, 1, Integer::sum);
            }
        }

        List<Set<AprioriItem>> prevFrequent = new ArrayList<>();
        for (Map.Entry<AprioriItem, Integer> e : itemCounts.entrySet()) {
            double support = e.getValue() / (double) n;
            if (support + 1e-12 >= minSupport) {
                Set<AprioriItem> set = new LinkedHashSet<>();
                set.add(e.getKey());
                prevFrequent.add(set);
                frequentAll.add(set);
                supportCounts.put(set, e.getValue());
            }
        }

        for (int k = 2; k <= maxItemsetSize && !prevFrequent.isEmpty(); k++) {
            Set<Set<AprioriItem>> candidates = generateCandidates(prevFrequent, k);
            Map<Set<AprioriItem>, Integer> counts = new LinkedHashMap<>();
            for (Set<AprioriItem> candidate : candidates) {
                int count = 0;
                for (Set<AprioriItem> t : tx) {
                    if (t.containsAll(candidate)) {
                        count++;
                    }
                }
                if (count / (double) n + 1e-12 >= minSupport) {
                    counts.put(candidate, count);
                }
            }
            prevFrequent = new ArrayList<>(counts.keySet());
            frequentAll.addAll(prevFrequent);
            supportCounts.putAll(counts);
        }

        List<AprioriRule> rules = generateRules(frequentAll, supportCounts, n, minConfidence)
                .stream()
                .sorted(Comparator.comparingDouble(AprioriRule::confidence).reversed()
                        .thenComparing(Comparator.comparingDouble(AprioriRule::lift).reversed()))
                .toList();

        List<FrequentItemsetDto> frequentDtos = frequentAll.stream()
                .map(s -> new FrequentItemsetDto(s, supportCounts.getOrDefault(s, 0), supportCounts.getOrDefault(s, 0) / (double) n))
                .sorted(Comparator.comparingDouble(FrequentItemsetDto::support).reversed())
                .toList();

        return new AprioriMiningResult(n, frequentDtos, rules);
    }

    @Transactional
    public PersistResult mineAndPersist(UUID batchId, UUID graphVersionId, double minSupport, double minConfidence, int maxItemsetSize) {
        AprioriMiningResult result = mine(batchId, minSupport, minConfidence, maxItemsetSize);
        int entityCount = 0;
        int relationCount = 0;
        for (AprioriRule rule : result.rules()) {
            // Persist pairwise rule edges only, which is easier for graph visualization and GAT.
            for (AprioriItem src : rule.antecedent()) {
                for (AprioriItem dst : rule.consequent()) {
                    if (src.key().equals(dst.key())) continue;
                    if (!shouldPersistRule(src, dst)) continue;
                    UUID sourceId = ensureKgEntity(src, graphVersionId);
                    UUID targetId = ensureKgEntity(dst, graphVersionId);
                    entityCount += sourceId != null ? 1 : 0;
                    entityCount += targetId != null ? 1 : 0;
                    if (sourceId != null && targetId != null && insertKgRelation(sourceId, targetId, relationType(src, dst), graphVersionId, rule)) {
                        relationCount++;
                    }
                }
            }
        }
        return new PersistResult(result.transactionCount(), result.rules().size(), entityCount, relationCount);
    }

    private Map<String, Set<AprioriItem>> loadTransactions(UUID batchId) {
        Map<String, Set<AprioriItem>> tx = new LinkedHashMap<>();
        List<Object> args = new ArrayList<>();
        String batchWhere = "";
        if (batchId != null) {
            batchWhere = " WHERE pr.batch_id = ? ";
            args.add(batchId);
        }

        // Steps and parameters.
        jdbcTemplate.query("""
                SELECT pr.run_id::text AS run_id,
                       ps.step_id::text AS step_id,
                       ps.step_name AS step_name,
                       pd.param_id::text AS param_id,
                       pd.param_name AS param_name,
                       pv.value_num,
                       pd.lower_limit,
                       pd.upper_limit,
                       pd.standard_value
                FROM prod.process_run pr
                JOIN core.process_step ps ON ps.step_id = pr.step_id
                LEFT JOIN prod.parameter_value pv ON pv.run_id = pr.run_id
                LEFT JOIN core.parameter_def pd ON pd.param_id = pv.param_id
                """ + batchWhere, rs -> {
            String runId = rs.getString("run_id");
            Set<AprioriItem> set = tx.computeIfAbsent(runId, k -> new LinkedHashSet<>());
            set.add(new AprioriItem("PROCESS_STEP", rs.getString("step_id"), rs.getString("step_name"), ""));
            String paramId = rs.getString("param_id");
            if (paramId != null) {
                set.add(new AprioriItem("PARAMETER", paramId, rs.getString("param_name"), parameterState(
                        getDouble(rs.getBigDecimal("value_num")),
                        getDouble(rs.getBigDecimal("lower_limit")),
                        getDouble(rs.getBigDecimal("upper_limit")),
                        getDouble(rs.getBigDecimal("standard_value"))
                )));
            }
        }, args.toArray());

        args.clear();
        batchWhere = "";
        if (batchId != null) {
            batchWhere = " WHERE pr.batch_id = ? ";
            args.add(batchId);
        }

        // Defects.
        jdbcTemplate.query("""
                SELECT it.run_id::text AS run_id,
                       dt.defect_type_id::text AS defect_type_id,
                       dt.defect_name AS defect_name,
                       COALESCE(dr.severity_level, dt.default_severity, 1) AS severity_level,
                       COALESCE(dr.defect_count, 1) AS defect_count
                FROM qc.inspection_task it
                JOIN prod.process_run pr ON pr.run_id = it.run_id
                JOIN qc.defect_record dr ON dr.inspection_id = it.inspection_id
                JOIN qc.defect_type dt ON dt.defect_type_id = dr.defect_type_id
                """ + batchWhere, rs -> {
            String runId = rs.getString("run_id");
            Set<AprioriItem> set = tx.computeIfAbsent(runId, k -> new LinkedHashSet<>());
            int severity = rs.getInt("severity_level");
            set.add(new AprioriItem("DEFECT_TYPE", rs.getString("defect_type_id"), rs.getString("defect_name"), severity >= 4 ? "CRITICAL" : "OBSERVED"));
        }, args.toArray());

        args.clear();
        batchWhere = "";
        if (batchId != null) {
            batchWhere = " WHERE pr.batch_id = ? ";
            args.add(batchId);
        }

        // Quality metrics.
        jdbcTemplate.query("""
                SELECT qm.run_id::text AS run_id,
                       qmd.metric_id::text AS metric_id,
                       qmd.metric_name AS metric_name,
                       qm.is_pass,
                       qm.deviation_value
                FROM qc.quality_measurement qm
                JOIN prod.process_run pr ON pr.run_id = qm.run_id
                JOIN qc.quality_metric_def qmd ON qmd.metric_id = qm.metric_id
                """ + batchWhere, rs -> {
            String runId = rs.getString("run_id");
            Set<AprioriItem> set = tx.computeIfAbsent(runId, k -> new LinkedHashSet<>());
            Boolean isPass = rs.getObject("is_pass") == null ? null : rs.getBoolean("is_pass");
            BigDecimal dev = rs.getBigDecimal("deviation_value");
            String state = Boolean.FALSE.equals(isPass) || (dev != null && dev.abs().doubleValue() > 0.000001) ? "ABNORMAL" : "PASS";
            set.add(new AprioriItem("QUALITY_METRIC", rs.getString("metric_id"), rs.getString("metric_name"), state));
        }, args.toArray());

        return tx;
    }

    private String parameterState(Double value, Double lower, Double upper, Double standard) {
        if (value == null) return "MISSING";
        if (lower != null && value < lower) return "LOW";
        if (upper != null && value > upper) return "HIGH";
        if (standard != null && Math.abs(value - standard) / Math.max(1.0, Math.abs(standard)) > 0.10) return value > standard ? "ABOVE_TARGET" : "BELOW_TARGET";
        return "NORMAL";
    }

    private Double getDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private Set<Set<AprioriItem>> generateCandidates(List<Set<AprioriItem>> prevFrequent, int k) {
        Set<Set<AprioriItem>> candidates = new LinkedHashSet<>();
        for (int i = 0; i < prevFrequent.size(); i++) {
            for (int j = i + 1; j < prevFrequent.size(); j++) {
                Set<AprioriItem> merged = new LinkedHashSet<>(prevFrequent.get(i));
                merged.addAll(prevFrequent.get(j));
                if (merged.size() == k && allSubsetsFrequent(merged, prevFrequent)) {
                    candidates.add(sortedSet(merged));
                }
            }
        }
        return candidates;
    }

    private boolean allSubsetsFrequent(Set<AprioriItem> candidate, List<Set<AprioriItem>> prevFrequent) {
        Set<Set<AprioriItem>> frequent = new HashSet<>(prevFrequent);
        for (AprioriItem item : candidate) {
            Set<AprioriItem> subset = new LinkedHashSet<>(candidate);
            subset.remove(item);
            if (!frequent.contains(sortedSet(subset))) return false;
        }
        return true;
    }

    private Set<AprioriItem> sortedSet(Set<AprioriItem> input) {
        return input.stream().sorted().collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<AprioriRule> generateRules(List<Set<AprioriItem>> frequentItemsets,
                                            Map<Set<AprioriItem>, Integer> supportCounts,
                                            int txCount,
                                            double minConfidence) {
        List<AprioriRule> rules = new ArrayList<>();
        for (Set<AprioriItem> itemset : frequentItemsets) {
            if (itemset.size() < 2) continue;
            List<AprioriItem> items = new ArrayList<>(itemset);
            int fullCount = supportCounts.getOrDefault(itemset, 0);
            List<Set<AprioriItem>> subsets = properNonEmptySubsets(items);
            for (Set<AprioriItem> antecedent : subsets) {
                Set<AprioriItem> consequent = new LinkedHashSet<>(itemset);
                consequent.removeAll(antecedent);
                if (consequent.isEmpty()) continue;
                Integer antCount = supportCounts.get(sortedSet(antecedent));
                Integer conCount = supportCounts.get(sortedSet(consequent));
                if (antCount == null || antCount == 0 || conCount == null || conCount == 0) continue;
                double support = fullCount / (double) txCount;
                double confidence = fullCount / (double) antCount;
                double lift = confidence / (conCount / (double) txCount);
                if (confidence + 1e-12 >= minConfidence) {
                    rules.add(new AprioriRule(sortedSet(antecedent), sortedSet(consequent), support, confidence, lift, fullCount, txCount));
                }
            }
        }
        return rules;
    }

    private List<Set<AprioriItem>> properNonEmptySubsets(List<AprioriItem> items) {
        int n = items.size();
        List<Set<AprioriItem>> subsets = new ArrayList<>();
        int maxMask = (1 << n) - 1;
        for (int mask = 1; mask < maxMask; mask++) {
            Set<AprioriItem> subset = new LinkedHashSet<>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) subset.add(items.get(i));
            }
            subsets.add(sortedSet(subset));
        }
        return subsets;
    }

    private boolean shouldPersistRule(AprioriItem src, AprioriItem dst) {
        if ("PARAMETER".equals(src.type()) && ("DEFECT_TYPE".equals(dst.type()) || "QUALITY_METRIC".equals(dst.type()))) return true;
        if ("PROCESS_STEP".equals(src.type()) && ("PARAMETER".equals(dst.type()) || "DEFECT_TYPE".equals(dst.type()) || "QUALITY_METRIC".equals(dst.type()))) return true;
        return "DEFECT_TYPE".equals(dst.type()) || "QUALITY_METRIC".equals(dst.type());
    }

    private String relationType(AprioriItem src, AprioriItem dst) {
        if ("PARAMETER".equals(src.type()) && "DEFECT_TYPE".equals(dst.type())) return "CAUSES";
        if ("PARAMETER".equals(src.type()) && "QUALITY_METRIC".equals(dst.type())) return "AFFECTS";
        if ("PROCESS_STEP".equals(src.type()) && "PARAMETER".equals(dst.type())) return "HAS_PARAMETER";
        if ("PROCESS_STEP".equals(src.type()) && "DEFECT_TYPE".equals(dst.type())) return "HAS_DEFECT";
        if ("PROCESS_STEP".equals(src.type()) && "QUALITY_METRIC".equals(dst.type())) return "HAS_QUALITY_METRIC";
        return "CO_OCCURS_WITH";
    }

    private UUID ensureKgEntity(AprioriItem item, UUID graphVersionId) {
        if (item.refId() == null || item.refId().isBlank()) return null;
        UUID refId;
        try {
            refId = UUID.fromString(item.refId());
        } catch (Exception e) {
            return null;
        }
        List<UUID> existing = jdbcTemplate.query("""
                SELECT entity_id FROM kg.kg_entity
                WHERE entity_type = ? AND ref_id = ?
                LIMIT 1
                """, (rs, rowNum) -> (UUID) rs.getObject("entity_id"), item.type(), refId);
        if (!existing.isEmpty()) return existing.get(0);

        UUID entityId = UUID.randomUUID();
        String[] ref = refForType(item.type());
        jdbcTemplate.update("""
                INSERT INTO kg.kg_entity(entity_id, graph_version_id, entity_type, ref_schema, ref_table, ref_id, entity_code, entity_name, properties, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
                """, entityId, graphVersionId, item.type(), ref[0], ref[1], refId, null, item.name(), toJson(Map.of("state", item.state(), "source", "APRIORI")));
        return entityId;
    }

    private String[] refForType(String type) {
        return switch (type) {
            case "PROCESS_STEP" -> new String[]{"core", "process_step"};
            case "PARAMETER" -> new String[]{"core", "parameter_def"};
            case "DEFECT_TYPE" -> new String[]{"qc", "defect_type"};
            case "QUALITY_METRIC" -> new String[]{"qc", "quality_metric_def"};
            default -> new String[]{null, null};
        };
    }

    private boolean insertKgRelation(UUID sourceId, UUID targetId, String relationType, UUID graphVersionId, AprioriRule rule) {
        Integer exists = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM kg.kg_relation
                WHERE source_entity_id = ? AND target_entity_id = ? AND relation_type = ?
                """, Integer.class, sourceId, targetId, relationType);
        if (exists != null && exists > 0) return false;
        jdbcTemplate.update("""
                INSERT INTO kg.kg_relation(relation_id, graph_version_id, source_entity_id, target_entity_id, relation_type, relation_weight, confidence, evidence_source, evidence_payload, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'APRIORI', ?::jsonb, now())
                """, UUID.randomUUID(), graphVersionId, sourceId, targetId, relationType,
                bd(rule.lift()), bd(rule.confidence()), toJson(Map.of(
                        "support", rule.support(),
                        "supportCount", rule.supportCount(),
                        "transactionCount", rule.transactionCount(),
                        "antecedent", labels(rule.antecedent()),
                        "consequent", labels(rule.consequent())
                )));
        return true;
    }

    private List<String> labels(Set<AprioriItem> items) {
        return items.stream().map(AprioriItem::displayName).toList();
    }

    private BigDecimal bd(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) v = 0.0;
        return BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    public record FrequentItemsetDto(Set<AprioriItem> items, int supportCount, double support) {}
    public record AprioriMiningResult(int transactionCount, List<FrequentItemsetDto> frequentItemsets, List<AprioriRule> rules) {}
    public record PersistResult(int transactionCount, int minedRuleCount, int touchedEntityCount, int insertedRelationCount) {}
}

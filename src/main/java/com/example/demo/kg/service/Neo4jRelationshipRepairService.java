package com.example.demo.kg.service;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Repairs relationship directions in Neo4j after old generic sync logic created FK -> owner directions. */
@Service
public class Neo4jRelationshipRepairService {

    private final Driver neo4jDriver;

    public Neo4jRelationshipRepairService(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    public Map<String, Integer> repairDirections() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Session session = neo4jDriver.session()) {
            counts.put("HAS_WORKSTATION", reverse(session, "Workstation", "HAS_WORKSTATION", "ProcessStep"));
            counts.put("HAS_EQUIPMENT", reverse(session, "Equipment", "HAS_EQUIPMENT", "Workstation"));
            counts.put("HAS_PARAMETER", reverse(session, "ParameterDef", "HAS_PARAMETER", "ProcessStep"));
            counts.put("HAS_UNIT", reverse(session, "ProductUnit", "HAS_UNIT", "ProductionBatch"));
            counts.put("HAS_RUN", reverse(session, "ProcessRun", "HAS_RUN", "ProductUnit"));
            counts.put("HAS_PARAM_VALUE", reverse(session, "ParameterValue", "HAS_PARAM_VALUE", "ProcessRun"));
            counts.put("HAS_INSPECTION", reverse(session, "InspectionTask", "HAS_INSPECTION", "ProcessRun"));
            counts.put("FOUND_DEFECT", reverse(session, "DefectRecord", "FOUND_DEFECT", "InspectionTask"));
            counts.put("SEMANTIC_RELATION", createSemanticRelations(session));
        }
        return counts;
    }

    private int reverse(Session session, String wrongSourceLabel, String relType, String wrongTargetLabel) {
        String cypher = String.format("""
                MATCH (src:%s)-[r:%s]->(dst:%s)
                WITH src, dst, r, properties(r) AS props
                MERGE (dst)-[nr:%s]->(src)
                SET nr += props,
                    nr.repairedAt = datetime(),
                    nr.direction = 'NORMALIZED'
                DELETE r
                RETURN count(nr) AS count
                """, wrongSourceLabel, relType, wrongTargetLabel, relType);
        Result result = session.run(cypher);
        return result.single().get("count").asInt(0);
    }

    /** Adds stable domain-level semantic edges useful for KG/GAT analysis. */
    private int createSemanticRelations(Session session) {
        Result result = session.run("""
                MATCH (ps:ProcessStep)<-[:HAS_WORKSTATION]-(wrong)
                RETURN 0 AS count
                """);
        // no-op compatibility guard above; actual relations below.
        int count = 0;
        count += session.run("""
                MATCH (ps:ProcessStep)-[:HAS_PARAMETER]->(p:ParameterDef)
                WITH ps, p
                MERGE (p)-[r:BELONGS_TO_STEP]->(ps)
                SET r.source = 'NEO4J_REPAIR', r.updatedAt = datetime()
                RETURN count(r) AS count
                """).single().get("count").asInt(0);
        count += session.run("""
                MATCH (ps:ProcessStep)
                MATCH (dt:DefectType {stepId: ps.stepId})
                MERGE (ps)-[r:HAS_DEFECT]->(dt)
                SET r.source = 'NEO4J_REPAIR', r.updatedAt = datetime()
                RETURN count(r) AS count
                """).single().get("count").asInt(0);
        count += session.run("""
                MATCH (ps:ProcessStep)
                MATCH (qm:QualityMetricDef {stepId: ps.stepId})
                MERGE (ps)-[r:HAS_QUALITY_METRIC]->(qm)
                SET r.source = 'NEO4J_REPAIR', r.updatedAt = datetime()
                RETURN count(r) AS count
                """).single().get("count").asInt(0);
        return count;
    }
}

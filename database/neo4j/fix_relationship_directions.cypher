// Neo4j relationship direction repair script.
// Run after old sync if generic FK direction created child -> parent edges.
// It preserves relationship properties and deletes wrong-direction edges.

MATCH (src:Workstation)-[r:HAS_WORKSTATION]->(dst:ProcessStep)
WITH src, dst, r, properties(r) AS props
MERGE (dst)-[nr:HAS_WORKSTATION]->(src)
SET nr += props, nr.repairedAt = datetime(), nr.direction = 'NORMALIZED'
DELETE r;

MATCH (src:Equipment)-[r:HAS_EQUIPMENT]->(dst:Workstation)
WITH src, dst, r, properties(r) AS props
MERGE (dst)-[nr:HAS_EQUIPMENT]->(src)
SET nr += props, nr.repairedAt = datetime(), nr.direction = 'NORMALIZED'
DELETE r;

MATCH (src:ParameterDef)-[r:HAS_PARAMETER]->(dst:ProcessStep)
WITH src, dst, r, properties(r) AS props
MERGE (dst)-[nr:HAS_PARAMETER]->(src)
SET nr += props, nr.repairedAt = datetime(), nr.direction = 'NORMALIZED'
DELETE r;

MATCH (src:ProductUnit)-[r:HAS_UNIT]->(dst:ProductionBatch)
WITH src, dst, r, properties(r) AS props
MERGE (dst)-[nr:HAS_UNIT]->(src)
SET nr += props, nr.repairedAt = datetime(), nr.direction = 'NORMALIZED'
DELETE r;

MATCH (src:ProcessRun)-[r:HAS_RUN]->(dst:ProductUnit)
WITH src, dst, r, properties(r) AS props
MERGE (dst)-[nr:HAS_RUN]->(src)
SET nr += props, nr.repairedAt = datetime(), nr.direction = 'NORMALIZED'
DELETE r;

MATCH (src:ParameterValue)-[r:HAS_PARAM_VALUE]->(dst:ProcessRun)
WITH src, dst, r, properties(r) AS props
MERGE (dst)-[nr:HAS_PARAM_VALUE]->(src)
SET nr += props, nr.repairedAt = datetime(), nr.direction = 'NORMALIZED'
DELETE r;

MATCH (src:InspectionTask)-[r:HAS_INSPECTION]->(dst:ProcessRun)
WITH src, dst, r, properties(r) AS props
MERGE (dst)-[nr:HAS_INSPECTION]->(src)
SET nr += props, nr.repairedAt = datetime(), nr.direction = 'NORMALIZED'
DELETE r;

MATCH (src:DefectRecord)-[r:FOUND_DEFECT]->(dst:InspectionTask)
WITH src, dst, r, properties(r) AS props
MERGE (dst)-[nr:FOUND_DEFECT]->(src)
SET nr += props, nr.repairedAt = datetime(), nr.direction = 'NORMALIZED'
DELETE r;

// Add stable semantic edges for graph analysis.
MATCH (ps:ProcessStep)-[:HAS_PARAMETER]->(p:ParameterDef)
MERGE (p)-[r:BELONGS_TO_STEP]->(ps)
SET r.source = 'REPAIR_SCRIPT', r.updatedAt = datetime();

MATCH (ps:ProcessStep)
MATCH (dt:DefectType {stepId: ps.stepId})
MERGE (ps)-[r:HAS_DEFECT]->(dt)
SET r.source = 'REPAIR_SCRIPT', r.updatedAt = datetime();

MATCH (ps:ProcessStep)
MATCH (qm:QualityMetricDef {stepId: ps.stepId})
MERGE (ps)-[r:HAS_QUALITY_METRIC]->(qm)
SET r.source = 'REPAIR_SCRIPT', r.updatedAt = datetime();

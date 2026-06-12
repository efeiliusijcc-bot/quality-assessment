-- ============================================================
-- 质量缺陷数据立方体与元数据共享扩展脚本
-- Target DB: PostgreSQL
-- ============================================================
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE SCHEMA IF NOT EXISTS etl;
CREATE SCHEMA IF NOT EXISTS qc;

CREATE TABLE IF NOT EXISTS etl.quality_metadata_catalog (
    metadata_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_domain        VARCHAR(64) NOT NULL,
    source_schema      VARCHAR(64) NOT NULL,
    source_table       VARCHAR(128) NOT NULL,
    source_field       VARCHAR(128) NOT NULL,
    field_name_cn      VARCHAR(128),
    field_name_en      VARCHAR(128),
    data_type          VARCHAR(64),
    unit               VARCHAR(64),
    business_meaning   TEXT,
    share_level        VARCHAR(32) DEFAULT 'INTERNAL',
    api_path           VARCHAR(255),
    is_dimension       BOOLEAN DEFAULT FALSE,
    is_measure         BOOLEAN DEFAULT FALSE,
    description        TEXT,
    created_at         TIMESTAMPTZ DEFAULT now(),
    updated_at         TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE etl.quality_metadata_catalog IS '质量问题数据共享元数据目录';
CREATE INDEX IF NOT EXISTS idx_quality_metadata_domain ON etl.quality_metadata_catalog(data_domain);
CREATE INDEX IF NOT EXISTS idx_quality_metadata_source ON etl.quality_metadata_catalog(source_schema, source_table);
CREATE UNIQUE INDEX IF NOT EXISTS ux_quality_metadata_catalog_field
    ON etl.quality_metadata_catalog(data_domain, source_schema, source_table, source_field);

DROP MATERIALIZED VIEW IF EXISTS qc.mv_defect_cube_by_batch_step;
DROP MATERIALIZED VIEW IF EXISTS qc.mv_defect_cube_by_step_type;
DROP MATERIALIZED VIEW IF EXISTS qc.mv_defect_cube_by_equipment;
DROP MATERIALIZED VIEW IF EXISTS qc.mv_defect_cube_by_time;
DROP MATERIALIZED VIEW IF EXISTS qc.mv_defect_cube_by_severity;

CREATE OR REPLACE VIEW qc.v_defect_cube_detail AS
SELECT
    dr.defect_id,
    COALESCE(dr.defect_count, 1) AS defect_count,
    COALESCE(dr.defect_size, 0) AS defect_size,
    COALESCE(dr.defect_area, 0) AS defect_area,
    dr.confidence,
    dr.severity_level,
    COALESCE(dr.is_critical, false) AS is_critical,
    dr.location_x,
    dr.location_y,
    dr.location_z,
    dr.created_at,
    dt.defect_type_id,
    dt.defect_code,
    dt.defect_name,
    dt.defect_category,
    it.inspection_id,
    it.inspection_type,
    it.model_name,
    it.model_version,
    it.result_status,
    pr.run_id,
    pr.run_no,
    pr.start_time,
    pr.end_time,
    pr.run_status,
    b.batch_id,
    b.batch_no,
    pu.unit_id,
    pu.serial_no,
    ps.step_id,
    ps.step_code,
    ps.step_name,
    ps.step_order,
    ws.station_id,
    ws.station_code,
    ws.station_name,
    eq.equipment_id,
    eq.equipment_code,
    eq.equipment_name
FROM qc.defect_record dr
JOIN qc.defect_type dt ON dr.defect_type_id = dt.defect_type_id
JOIN qc.inspection_task it ON dr.inspection_id = it.inspection_id
JOIN prod.process_run pr ON it.run_id = pr.run_id
JOIN prod.production_batch b ON pr.batch_id = b.batch_id
LEFT JOIN prod.product_unit pu ON dr.unit_id = pu.unit_id
LEFT JOIN core.process_step ps ON pr.step_id = ps.step_id
LEFT JOIN core.workstation ws ON pr.station_id = ws.station_id
LEFT JOIN core.equipment eq ON pr.equipment_id = eq.equipment_id;

COMMENT ON VIEW qc.v_defect_cube_detail IS '质量缺陷数据立方体标准明细视图';

CREATE MATERIALIZED VIEW qc.mv_defect_cube_by_batch_step AS
SELECT
    batch_id, batch_no, step_id, step_code, step_name,
    COUNT(defect_id) AS defect_record_count,
    SUM(defect_count) AS defect_total,
    SUM(CASE WHEN is_critical THEN defect_count ELSE 0 END) AS critical_defect_total,
    AVG(NULLIF(confidence, 0)) AS avg_confidence,
    SUM(defect_area) AS total_defect_area,
    AVG(NULLIF(defect_size, 0)) AS avg_defect_size,
    MIN(created_at) AS first_detected_at,
    MAX(created_at) AS last_detected_at
FROM qc.v_defect_cube_detail
GROUP BY batch_id, batch_no, step_id, step_code, step_name;

CREATE INDEX IF NOT EXISTS idx_mv_defect_cube_batch_step_batch ON qc.mv_defect_cube_by_batch_step(batch_no);
CREATE INDEX IF NOT EXISTS idx_mv_defect_cube_batch_step_step ON qc.mv_defect_cube_by_batch_step(step_code);
COMMENT ON MATERIALIZED VIEW qc.mv_defect_cube_by_batch_step IS '质量缺陷数据立方体：按批次、工序统计';

CREATE MATERIALIZED VIEW qc.mv_defect_cube_by_step_type AS
SELECT
    step_id, step_code, step_name,
    defect_type_id, defect_code, defect_name, defect_category,
    COUNT(defect_id) AS defect_record_count,
    SUM(defect_count) AS defect_total,
    SUM(CASE WHEN is_critical THEN defect_count ELSE 0 END) AS critical_defect_total,
    AVG(NULLIF(confidence, 0)) AS avg_confidence,
    SUM(defect_area) AS total_defect_area,
    AVG(NULLIF(defect_size, 0)) AS avg_defect_size,
    MAX(created_at) AS latest_detected_at
FROM qc.v_defect_cube_detail
GROUP BY step_id, step_code, step_name, defect_type_id, defect_code, defect_name, defect_category;

CREATE INDEX IF NOT EXISTS idx_mv_defect_cube_step_type_step ON qc.mv_defect_cube_by_step_type(step_code);
CREATE INDEX IF NOT EXISTS idx_mv_defect_cube_step_type_defect ON qc.mv_defect_cube_by_step_type(defect_code);
COMMENT ON MATERIALIZED VIEW qc.mv_defect_cube_by_step_type IS '质量缺陷数据立方体：按工序、缺陷类型统计';

CREATE MATERIALIZED VIEW qc.mv_defect_cube_by_equipment AS
SELECT
    equipment_id, equipment_code, equipment_name,
    station_id, station_code, station_name,
    step_id, step_code, step_name,
    COUNT(defect_id) AS defect_record_count,
    SUM(defect_count) AS defect_total,
    SUM(CASE WHEN is_critical THEN defect_count ELSE 0 END) AS critical_defect_total,
    AVG(NULLIF(confidence, 0)) AS avg_confidence,
    SUM(defect_area) AS total_defect_area,
    MAX(created_at) AS latest_detected_at
FROM qc.v_defect_cube_detail
GROUP BY equipment_id, equipment_code, equipment_name, station_id, station_code, station_name, step_id, step_code, step_name;

CREATE INDEX IF NOT EXISTS idx_mv_defect_cube_equipment ON qc.mv_defect_cube_by_equipment(equipment_code);
COMMENT ON MATERIALIZED VIEW qc.mv_defect_cube_by_equipment IS '质量缺陷数据立方体：按设备统计';

CREATE MATERIALIZED VIEW qc.mv_defect_cube_by_time AS
SELECT
    date_trunc('day', created_at)::date AS stat_date,
    step_id, step_code, step_name,
    defect_type_id, defect_code, defect_name,
    COUNT(defect_id) AS defect_record_count,
    SUM(defect_count) AS defect_total,
    SUM(CASE WHEN is_critical THEN defect_count ELSE 0 END) AS critical_defect_total,
    AVG(NULLIF(confidence, 0)) AS avg_confidence,
    SUM(defect_area) AS total_defect_area
FROM qc.v_defect_cube_detail
GROUP BY date_trunc('day', created_at)::date, step_id, step_code, step_name, defect_type_id, defect_code, defect_name;

CREATE INDEX IF NOT EXISTS idx_mv_defect_cube_time_date ON qc.mv_defect_cube_by_time(stat_date);
CREATE INDEX IF NOT EXISTS idx_mv_defect_cube_time_step ON qc.mv_defect_cube_by_time(step_code);
COMMENT ON MATERIALIZED VIEW qc.mv_defect_cube_by_time IS '质量缺陷数据立方体：按时间趋势统计';

CREATE MATERIALIZED VIEW qc.mv_defect_cube_by_severity AS
SELECT
    COALESCE(severity_level::text, 'UNKNOWN') AS severity_level,
    step_id, step_code, step_name,
    defect_type_id, defect_code, defect_name,
    COUNT(defect_id) AS defect_record_count,
    SUM(defect_count) AS defect_total,
    AVG(NULLIF(confidence, 0)) AS avg_confidence,
    SUM(defect_area) AS total_defect_area,
    MAX(created_at) AS latest_detected_at
FROM qc.v_defect_cube_detail
GROUP BY COALESCE(severity_level::text, 'UNKNOWN'), step_id, step_code, step_name, defect_type_id, defect_code, defect_name;

CREATE INDEX IF NOT EXISTS idx_mv_defect_cube_severity ON qc.mv_defect_cube_by_severity(severity_level);
COMMENT ON MATERIALIZED VIEW qc.mv_defect_cube_by_severity IS '质量缺陷数据立方体：按严重等级统计';

INSERT INTO etl.quality_metadata_catalog
(data_domain, source_schema, source_table, source_field, field_name_cn, field_name_en, data_type, unit, business_meaning, share_level, api_path, is_dimension, is_measure, description)
VALUES
('defect_cube', 'qc', 'v_defect_cube_detail', 'batch_no', '批次号', 'batchNo', 'varchar', NULL, '生产批次维度，用于按批次追溯质量缺陷', 'INTERNAL', '/api/quality-cube/by-batch-step', TRUE, FALSE, '质量缺陷数据立方体批次维度'),
('defect_cube', 'qc', 'v_defect_cube_detail', 'step_name', '工序名称', 'stepName', 'varchar', NULL, '工序维度，用于识别不同关键工序的缺陷分布', 'INTERNAL', '/api/quality-cube/by-step-type', TRUE, FALSE, '质量缺陷数据立方体工序维度'),
('defect_cube', 'qc', 'v_defect_cube_detail', 'equipment_code', '设备编码', 'equipmentCode', 'varchar', NULL, '设备维度，用于分析缺陷与设备状态之间的关系', 'INTERNAL', '/api/quality-cube/by-equipment', TRUE, FALSE, '质量缺陷数据立方体设备维度'),
('defect_cube', 'qc', 'v_defect_cube_detail', 'defect_name', '缺陷类型', 'defectName', 'varchar', NULL, '缺陷类型维度，用于统计不同缺陷类别的发生规律', 'INTERNAL', '/api/quality-cube/by-step-type', TRUE, FALSE, '质量缺陷数据立方体缺陷类型维度'),
('defect_cube', 'qc', 'v_defect_cube_detail', 'severity_level', '严重等级', 'severityLevel', 'varchar', NULL, '严重等级维度，用于识别高风险质量问题', 'INTERNAL', '/api/quality-cube/by-severity', TRUE, FALSE, '质量缺陷数据立方体风险等级维度'),
('defect_cube', 'qc', 'v_defect_cube_detail', 'created_at', '检测时间', 'createdAt', 'timestamptz', NULL, '时间维度，用于分析缺陷趋势', 'INTERNAL', '/api/quality-cube/by-time', TRUE, FALSE, '质量缺陷数据立方体时间维度'),
('defect_cube', 'qc', 'v_defect_cube_detail', 'defect_count', '缺陷数量', 'defectCount', 'numeric', '个', '度量指标，用于统计缺陷数量', 'INTERNAL', '/api/quality-cube/overview', FALSE, TRUE, '质量缺陷数量指标'),
('defect_cube', 'qc', 'v_defect_cube_detail', 'defect_area', '缺陷面积', 'defectArea', 'numeric', 'px2/mm2', '度量指标，用于统计缺陷影响范围', 'INTERNAL', '/api/quality-cube/overview', FALSE, TRUE, '质量缺陷面积指标'),
('defect_cube', 'qc', 'v_defect_cube_detail', 'confidence', '识别置信度', 'confidence', 'numeric', NULL, '度量指标，用于衡量模型识别可信度', 'INTERNAL', '/api/quality-cube/overview', FALSE, TRUE, '缺陷识别置信度指标')
ON CONFLICT (data_domain, source_schema, source_table, source_field) DO UPDATE SET
    field_name_cn = EXCLUDED.field_name_cn,
    field_name_en = EXCLUDED.field_name_en,
    data_type = EXCLUDED.data_type,
    unit = EXCLUDED.unit,
    business_meaning = EXCLUDED.business_meaning,
    share_level = EXCLUDED.share_level,
    api_path = EXCLUDED.api_path,
    is_dimension = EXCLUDED.is_dimension,
    is_measure = EXCLUDED.is_measure,
    description = EXCLUDED.description,
    updated_at = now();

CREATE OR REPLACE FUNCTION qc.refresh_defect_cube()
RETURNS TEXT
LANGUAGE plpgsql
AS $$
BEGIN
    REFRESH MATERIALIZED VIEW qc.mv_defect_cube_by_batch_step;
    REFRESH MATERIALIZED VIEW qc.mv_defect_cube_by_step_type;
    REFRESH MATERIALIZED VIEW qc.mv_defect_cube_by_equipment;
    REFRESH MATERIALIZED VIEW qc.mv_defect_cube_by_time;
    REFRESH MATERIALIZED VIEW qc.mv_defect_cube_by_severity;
    RETURN 'quality defect cube refreshed at ' || now();
END;
$$;

COMMENT ON FUNCTION qc.refresh_defect_cube() IS '刷新质量缺陷数据立方体相关物化视图';

package com.example.demo.qc.cube;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QualityCubeService {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public QualityCubeService(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> totals = jdbcTemplate.queryForMap("""
            SELECT COALESCE(SUM(defect_total), 0) AS defect_total,
                   COALESCE(SUM(critical_defect_total), 0) AS critical_defect_total,
                   COALESCE(AVG(avg_confidence), 0) AS avg_confidence,
                   COALESCE(SUM(total_defect_area), 0) AS total_defect_area,
                   COUNT(DISTINCT batch_no) AS batch_count,
                   COUNT(DISTINCT step_code) AS step_count
            FROM qc.mv_defect_cube_by_batch_step
            """);
        List<Map<String, Object>> topStep = jdbcTemplate.queryForList("""
            SELECT step_code, step_name, defect_total
            FROM qc.mv_defect_cube_by_batch_step
            ORDER BY defect_total DESC NULLS LAST
            LIMIT 1
            """);
        List<Map<String, Object>> topDefect = jdbcTemplate.queryForList("""
            SELECT defect_code, defect_name, defect_total
            FROM qc.mv_defect_cube_by_step_type
            ORDER BY defect_total DESC NULLS LAST
            LIMIT 1
            """);
        List<Map<String, Object>> topEquipment = jdbcTemplate.queryForList("""
            SELECT equipment_code, equipment_name, defect_total
            FROM qc.mv_defect_cube_by_equipment
            WHERE equipment_code IS NOT NULL
            ORDER BY defect_total DESC NULLS LAST
            LIMIT 1
            """);
        result.put("totals", totals);
        result.put("topStep", topStep.isEmpty() ? null : topStep.get(0));
        result.put("topDefect", topDefect.isEmpty() ? null : topDefect.get(0));
        result.put("topEquipment", topEquipment.isEmpty() ? null : topEquipment.get(0));
        result.put("generatedAt", new Date());
        return result;
    }

    public List<Map<String, Object>> byBatchStep(String batchNo, String stepCode, Integer limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT batch_id, batch_no, step_id, step_code, step_name,
                   defect_record_count, defect_total, critical_defect_total,
                   avg_confidence, total_defect_area, avg_defect_size,
                   first_detected_at, last_detected_at
            FROM qc.mv_defect_cube_by_batch_step
            WHERE 1 = 1
            """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (hasText(batchNo)) {
            sql.append(" AND batch_no = :batchNo");
            params.addValue("batchNo", batchNo);
        }
        if (hasText(stepCode)) {
            sql.append(" AND step_code = :stepCode");
            params.addValue("stepCode", stepCode);
        }
        sql.append(" ORDER BY defect_total DESC NULLS LAST LIMIT :limit");
        params.addValue("limit", normalizeLimit(limit));
        return namedJdbcTemplate.queryForList(sql.toString(), params);
    }

    public List<Map<String, Object>> byStepType(String stepCode, String defectCode, Integer limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT step_id, step_code, step_name,
                   defect_type_id, defect_code, defect_name, defect_category,
                   defect_record_count, defect_total, critical_defect_total,
                   avg_confidence, total_defect_area, avg_defect_size,
                   latest_detected_at
            FROM qc.mv_defect_cube_by_step_type
            WHERE 1 = 1
            """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (hasText(stepCode)) {
            sql.append(" AND step_code = :stepCode");
            params.addValue("stepCode", stepCode);
        }
        if (hasText(defectCode)) {
            sql.append(" AND defect_code = :defectCode");
            params.addValue("defectCode", defectCode);
        }
        sql.append(" ORDER BY defect_total DESC NULLS LAST LIMIT :limit");
        params.addValue("limit", normalizeLimit(limit));
        return namedJdbcTemplate.queryForList(sql.toString(), params);
    }

    public List<Map<String, Object>> byEquipment(String equipmentCode, Integer limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT equipment_id, equipment_code, equipment_name,
                   station_id, station_code, station_name,
                   step_id, step_code, step_name,
                   defect_record_count, defect_total, critical_defect_total,
                   avg_confidence, total_defect_area, latest_detected_at
            FROM qc.mv_defect_cube_by_equipment
            WHERE 1 = 1
            """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (hasText(equipmentCode)) {
            sql.append(" AND equipment_code = :equipmentCode");
            params.addValue("equipmentCode", equipmentCode);
        }
        sql.append(" ORDER BY defect_total DESC NULLS LAST LIMIT :limit");
        params.addValue("limit", normalizeLimit(limit));
        return namedJdbcTemplate.queryForList(sql.toString(), params);
    }

    public List<Map<String, Object>> byTime(LocalDate from, LocalDate to, String stepCode, Integer limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT stat_date, step_id, step_code, step_name,
                   defect_type_id, defect_code, defect_name,
                   defect_record_count, defect_total, critical_defect_total,
                   avg_confidence, total_defect_area
            FROM qc.mv_defect_cube_by_time
            WHERE 1 = 1
            """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (from != null) {
            sql.append(" AND stat_date >= :fromDate");
            params.addValue("fromDate", from);
        }
        if (to != null) {
            sql.append(" AND stat_date <= :toDate");
            params.addValue("toDate", to);
        }
        if (hasText(stepCode)) {
            sql.append(" AND step_code = :stepCode");
            params.addValue("stepCode", stepCode);
        }
        sql.append(" ORDER BY stat_date ASC, defect_total DESC NULLS LAST LIMIT :limit");
        params.addValue("limit", normalizeLimit(limit));
        return namedJdbcTemplate.queryForList(sql.toString(), params);
    }

    public List<Map<String, Object>> bySeverity(String severityLevel, Integer limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT severity_level, step_id, step_code, step_name,
                   defect_type_id, defect_code, defect_name,
                   defect_record_count, defect_total,
                   avg_confidence, total_defect_area,
                   latest_detected_at
            FROM qc.mv_defect_cube_by_severity
            WHERE 1 = 1
            """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (hasText(severityLevel)) {
            sql.append(" AND severity_level = :severityLevel");
            params.addValue("severityLevel", severityLevel);
        }
        sql.append(" ORDER BY defect_total DESC NULLS LAST LIMIT :limit");
        params.addValue("limit", normalizeLimit(limit));
        return namedJdbcTemplate.queryForList(sql.toString(), params);
    }

    public List<Map<String, Object>> metadata(String dataDomain) {
        StringBuilder sql = new StringBuilder("""
            SELECT metadata_id, data_domain, source_schema, source_table, source_field,
                   field_name_cn, field_name_en, data_type, unit, business_meaning,
                   share_level, api_path, is_dimension, is_measure, description,
                   created_at, updated_at
            FROM etl.quality_metadata_catalog
            WHERE 1 = 1
            """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (hasText(dataDomain)) {
            sql.append(" AND data_domain = :dataDomain");
            params.addValue("dataDomain", dataDomain);
        }
        sql.append(" ORDER BY data_domain, is_dimension DESC, is_measure DESC, source_table, source_field");
        return namedJdbcTemplate.queryForList(sql.toString(), params);
    }

    public Map<String, Object> refresh() {
        String message = jdbcTemplate.queryForObject("SELECT qc.refresh_defect_cube()", String.class);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("message", message);
        result.put("refreshedAt", new Date());
        return result;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private int normalizeLimit(Integer limit) {
        return limit == null ? 100 : Math.min(Math.max(limit, 1), 1000);
    }
}

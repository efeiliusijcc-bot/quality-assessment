-- PostgreSQL extension script for KG NLP lexicon and Apriori evidence.
-- Execute after the base schema/import scripts. The script is idempotent.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE SCHEMA IF NOT EXISTS kg;

ALTER TABLE kg.kg_entity
    ADD COLUMN IF NOT EXISTS properties jsonb;

ALTER TABLE kg.kg_relation
    ADD COLUMN IF NOT EXISTS evidence_source varchar(64),
    ADD COLUMN IF NOT EXISTS evidence_payload jsonb;

CREATE TABLE IF NOT EXISTS kg.domain_term (
    term_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    term_name varchar(255) NOT NULL,
    term_type varchar(64) NOT NULL,
    aliases text[] DEFAULT ARRAY[]::text[],
    source varchar(64) NOT NULL DEFAULT 'MANUAL',
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_domain_term_name_type UNIQUE(term_name, term_type)
);

COMMENT ON TABLE kg.domain_term IS '领域词典表，用于 Jieba 分词、实体识别和知识图谱实体抽取';
COMMENT ON COLUMN kg.domain_term.term_type IS 'PROCESS_STEP/PARAMETER/QUALITY_METRIC/DEFECT_TYPE/EQUIPMENT/WORKSTATION';

INSERT INTO kg.domain_term(term_name, term_type, aliases, source)
VALUES
('机械打孔', 'PROCESS_STEP', ARRAY['冲孔','机械冲孔'], 'INIT'),
('激光打孔', 'PROCESS_STEP', ARRAY['激光孔加工'], 'INIT'),
('通孔AOI', 'PROCESS_STEP', ARRAY['通孔检测','孔位AOI'], 'INIT'),
('填孔', 'PROCESS_STEP', ARRAY['通孔填充'], 'INIT'),
('印刷', 'PROCESS_STEP', ARRAY['丝网印刷','电极印刷'], 'INIT'),
('层压', 'PROCESS_STEP', ARRAY['压合','叠层压合'], 'INIT'),
('烧结', 'PROCESS_STEP', ARRAY['高温共烧'], 'INIT'),
('冲孔压力', 'PARAMETER', ARRAY['打孔压力'], 'INIT'),
('扫描速度', 'PARAMETER', ARRAY['激光扫描速度'], 'INIT'),
('离焦量', 'PARAMETER', ARRAY['激光离焦量'], 'INIT'),
('功率设定值', 'PARAMETER', ARRAY['激光功率设定'], 'INIT'),
('激光实测功率', 'PARAMETER', ARRAY['实测功率'], 'INIT'),
('层压压力', 'PARAMETER', ARRAY['压合压力'], 'INIT'),
('烧结温度', 'PARAMETER', ARRAY['共烧温度'], 'INIT'),
('中心孔偏移', 'DEFECT_TYPE', ARRAY['孔偏移','中心偏移'], 'INIT'),
('少孔', 'DEFECT_TYPE', ARRAY['漏孔'], 'INIT'),
('多孔', 'DEFECT_TYPE', ARRAY['孔数量偏大'], 'INIT'),
('孔内异物', 'DEFECT_TYPE', ARRAY['异物'], 'INIT'),
('漏填', 'DEFECT_TYPE', ARRAY['填孔不足'], 'INIT'),
('毛边', 'DEFECT_TYPE', ARRAY['印刷毛边'], 'INIT'),
('断线', 'DEFECT_TYPE', ARRAY['线路断裂'], 'INIT'),
('拖丝', 'DEFECT_TYPE', ARRAY['拉丝'], 'INIT'),
('孔锥度', 'QUALITY_METRIC', ARRAY['通孔锥度'], 'INIT'),
('叠层精度', 'QUALITY_METRIC', ARRAY['相邻层精度'], 'INIT'),
('基板翘曲度', 'QUALITY_METRIC', ARRAY['翘曲度'], 'INIT')
ON CONFLICT (term_name, term_type) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_domain_term_type ON kg.domain_term(term_type);
CREATE INDEX IF NOT EXISTS idx_domain_term_enabled ON kg.domain_term(enabled);
CREATE INDEX IF NOT EXISTS idx_kg_entity_ref ON kg.kg_entity(ref_schema, ref_table, ref_id);
CREATE INDEX IF NOT EXISTS idx_kg_relation_src_tgt_type ON kg.kg_relation(source_entity_id, target_entity_id, relation_type);
CREATE INDEX IF NOT EXISTS idx_kg_relation_evidence_source ON kg.kg_relation(evidence_source);

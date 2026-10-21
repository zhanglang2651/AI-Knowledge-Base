-- =====================================================================
-- AI 知识库问答平台 - 分块文本表（混合检索用）
-- 数据库：PostgreSQL 16 + pgvector
--
-- 为什么需要这张表：
--   向量表 vector_store 只存向量与文本，Spring AI 不暴露按条件查询文本的接口。
--   要做「关键词召回」就必须另有一份可查询的正文副本，
--   否则关键词分支无法实现 —— 这是混合检索的硬性前提。
--
-- 为什么不用 tsvector 中文分词：
--   PG 内置的 simple/chinese 分词器对中文支持有限（chinese 需要 zhparser 扩展，
--   而 pgvector 官方镜像不带）。因此本表先存原文，由应用层做二元切分后 LIKE 召回，
--   数据量到十万级后再替换为 pg_trgm GIN 索引或引入 zhparser，接口不变。
--
-- 规约：遵循 V1 的命名与必备字段约定，禁用外键，由应用层保证一致性。
-- =====================================================================

CREATE TABLE IF NOT EXISTS kb_chunk
(
    id           BIGSERIAL PRIMARY KEY,
    kb_id        BIGINT      NOT NULL,
    doc_id       BIGINT      NOT NULL,
    doc_name     VARCHAR(255) NOT NULL,
    page_no      INTEGER,
    chunk_no     INTEGER     NOT NULL,
    content      TEXT        NOT NULL,
    is_deleted   INTEGER     NOT NULL DEFAULT 0,
    gmt_create   TIMESTAMP   NOT NULL DEFAULT now(),
    gmt_modified TIMESTAMP   NOT NULL DEFAULT now()
);

COMMENT ON TABLE kb_chunk IS '文档分块正文表，供关键词召回使用；与 vector_store 一一对应';
COMMENT ON COLUMN kb_chunk.chunk_no IS '同一文档内从 1 开始的块序号';
COMMENT ON COLUMN kb_chunk.page_no IS '页码估算值，纯文本文档为 1';

-- 检索主路径：先按知识库过滤，再做正文匹配
CREATE INDEX IF NOT EXISTS idx_chunk_kb_id ON kb_chunk (kb_id);
CREATE INDEX IF NOT EXISTS idx_chunk_doc_id ON kb_chunk (doc_id);
-- 覆盖删除场景：按文档批量清理
CREATE INDEX IF NOT EXISTS idx_chunk_doc_kb ON kb_chunk (doc_id, kb_id);

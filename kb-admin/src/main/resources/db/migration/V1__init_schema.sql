-- =====================================================================
-- AI 知识库问答平台 - 初始化脚本
-- 数据库：PostgreSQL 16 + pgvector
-- 说明：
--   1. 本脚本只建业务表，vector_store 表由 Spring AI 的 PgVectorStore
--      在 initialize-schema=true 时自动创建（避免两边结构不一致）
--   2. 建表规约遵循《阿里巴巴 Java 开发手册》：
--      小写下划线命名、必备 gmt_create/gmt_modified、布尔字段语义清晰、
--      索引前缀 idx_ / uk_、禁用外键（由应用层保证一致性）
-- =====================================================================

-- pgvector 扩展：提供 vector 类型与向量相似度检索能力
CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------------
-- 用户表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user
(
    id           BIGSERIAL PRIMARY KEY,
    username     VARCHAR(64)  NOT NULL,
    password     VARCHAR(128) NOT NULL,
    nickname     VARCHAR(64),
    email        VARCHAR(128),
    role         VARCHAR(32)  NOT NULL DEFAULT 'USER',
    status       INTEGER      NOT NULL DEFAULT 1,
    is_deleted   INTEGER      NOT NULL DEFAULT 0,
    gmt_create   TIMESTAMP    NOT NULL DEFAULT now(),
    gmt_modified TIMESTAMP    NOT NULL DEFAULT now()
);

COMMENT ON TABLE sys_user IS '用户表';
COMMENT ON COLUMN sys_user.password IS 'BCrypt 哈希，禁止明文';
COMMENT ON COLUMN sys_user.role IS 'USER / ADMIN';
COMMENT ON COLUMN sys_user.status IS '1 启用，0 禁用';

-- 唯一索引带 is_deleted 条件：逻辑删除后用户名可被重新注册
CREATE UNIQUE INDEX IF NOT EXISTS uk_username ON sys_user (username) WHERE is_deleted = 0;

-- ---------------------------------------------------------------------
-- 知识库表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kb_knowledge_base
(
    id                   BIGSERIAL PRIMARY KEY,
    name                 VARCHAR(128) NOT NULL,
    description          VARCHAR(512),
    owner_id             BIGINT       NOT NULL,
    embedding_model      VARCHAR(64)  NOT NULL DEFAULT 'text-embedding-3-small',
    embedding_dim        INTEGER      NOT NULL DEFAULT 1536,
    chunk_size           INTEGER      NOT NULL DEFAULT 600,
    chunk_overlap        INTEGER      NOT NULL DEFAULT 80,
    top_k                INTEGER      NOT NULL DEFAULT 5,
    similarity_threshold NUMERIC(4, 3) NOT NULL DEFAULT 0.500,
    doc_count            INTEGER      NOT NULL DEFAULT 0,
    status               INTEGER      NOT NULL DEFAULT 1,
    is_deleted           INTEGER      NOT NULL DEFAULT 0,
    gmt_create           TIMESTAMP    NOT NULL DEFAULT now(),
    gmt_modified         TIMESTAMP    NOT NULL DEFAULT now()
);

COMMENT ON TABLE kb_knowledge_base IS '知识库';
COMMENT ON COLUMN kb_knowledge_base.embedding_dim IS '向量维度，必须与向量表一致，换模型需重建向量表';
COMMENT ON COLUMN kb_knowledge_base.similarity_threshold IS '相似度阈值 0-1，越高越严格';

CREATE INDEX IF NOT EXISTS idx_kb_owner ON kb_knowledge_base (owner_id, is_deleted);

-- ---------------------------------------------------------------------
-- 文档表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kb_document
(
    id           BIGSERIAL PRIMARY KEY,
    kb_id        BIGINT       NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    file_key     VARCHAR(512) NOT NULL,
    file_size    BIGINT       NOT NULL,
    file_type    VARCHAR(16)  NOT NULL,
    file_md5     VARCHAR(64)  NOT NULL,
    page_count   INTEGER      NOT NULL DEFAULT 0,
    chunk_count  INTEGER      NOT NULL DEFAULT 0,
    parse_status VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    retry_count  INTEGER      NOT NULL DEFAULT 0,
    error_msg    VARCHAR(1024),
    is_deleted   INTEGER      NOT NULL DEFAULT 0,
    gmt_create   TIMESTAMP    NOT NULL DEFAULT now(),
    gmt_modified TIMESTAMP    NOT NULL DEFAULT now()
);

COMMENT ON TABLE kb_document IS '文档';
COMMENT ON COLUMN kb_document.file_key IS '存储键，本地为相对路径';
COMMENT ON COLUMN kb_document.parse_status IS 'PENDING/PARSING/EMBEDDING/COMPLETED/FAILED';

CREATE INDEX IF NOT EXISTS idx_doc_kb ON kb_document (kb_id, is_deleted);

-- 同库内文件去重：同一份文件重复上传会产生重复向量，挤占检索名额
CREATE UNIQUE INDEX IF NOT EXISTS uk_doc_kb_md5 ON kb_document (kb_id, file_md5) WHERE is_deleted = 0;

-- ---------------------------------------------------------------------
-- 会话表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_conversation
(
    id              BIGSERIAL PRIMARY KEY,
    kb_id           BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    title           VARCHAR(128) NOT NULL DEFAULT '新对话',
    message_count   INTEGER      NOT NULL DEFAULT 0,
    last_message_at TIMESTAMP,
    is_deleted      INTEGER      NOT NULL DEFAULT 0,
    gmt_create      TIMESTAMP    NOT NULL DEFAULT now(),
    gmt_modified    TIMESTAMP    NOT NULL DEFAULT now()
);

COMMENT ON TABLE chat_conversation IS '问答会话';

CREATE INDEX IF NOT EXISTS idx_conv_user ON chat_conversation (user_id, last_message_at DESC);

-- ---------------------------------------------------------------------
-- 消息表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_message
(
    id                BIGSERIAL PRIMARY KEY,
    conversation_id   BIGINT      NOT NULL,
    role              VARCHAR(16) NOT NULL,
    content           TEXT        NOT NULL,
    model             VARCHAR(64),
    prompt_tokens     INTEGER     NOT NULL DEFAULT 0,
    completion_tokens INTEGER     NOT NULL DEFAULT 0,
    latency_ms        INTEGER     NOT NULL DEFAULT 0,
    sources_json      TEXT,
    gmt_create        TIMESTAMP   NOT NULL DEFAULT now()
);

COMMENT ON TABLE chat_message IS '对话消息';
COMMENT ON COLUMN chat_message.role IS 'user / assistant';
COMMENT ON COLUMN chat_message.sources_json IS '引用来源快照，保证历史回答的引用不随知识库变更而错乱';

CREATE INDEX IF NOT EXISTS idx_msg_conv ON chat_message (conversation_id, id);

-- ---------------------------------------------------------------------
-- 模型配置表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_model_config
(
    id           BIGSERIAL PRIMARY KEY,
    provider     VARCHAR(32)    NOT NULL,
    model_name   VARCHAR(64)    NOT NULL,
    model_type   VARCHAR(16)    NOT NULL,
    base_url     VARCHAR(255),
    api_key      VARCHAR(512)   NOT NULL,
    input_price  NUMERIC(10, 6),
    output_price NUMERIC(10, 6),
    is_default   INTEGER        NOT NULL DEFAULT 0,
    status       INTEGER        NOT NULL DEFAULT 1,
    gmt_create   TIMESTAMP      NOT NULL DEFAULT now(),
    gmt_modified TIMESTAMP      NOT NULL DEFAULT now()
);

COMMENT ON TABLE ai_model_config IS '模型配置';
COMMENT ON COLUMN ai_model_config.api_key IS 'AES 加密存储，禁止明文，接口回显需打码';
COMMENT ON COLUMN ai_model_config.model_type IS 'chat / embedding';

-- ---------------------------------------------------------------------
-- AI 调用日志表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_call_log
(
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT         NOT NULL,
    scene             VARCHAR(32)    NOT NULL,
    model             VARCHAR(64)    NOT NULL,
    prompt_tokens     INTEGER        NOT NULL DEFAULT 0,
    completion_tokens INTEGER        NOT NULL DEFAULT 0,
    cost              NUMERIC(12, 6) NOT NULL DEFAULT 0,
    latency_ms        INTEGER        NOT NULL DEFAULT 0,
    is_success        INTEGER        NOT NULL DEFAULT 1,
    error_code        VARCHAR(16),
    trace_id          VARCHAR(64),
    gmt_create        TIMESTAMP      NOT NULL DEFAULT now()
);

COMMENT ON TABLE ai_call_log IS 'AI 调用明细日志，用于成本核算与故障排查';

CREATE INDEX IF NOT EXISTS idx_call_user_time ON ai_call_log (user_id, gmt_create DESC);

-- 日聚合表：看板只查这张表，避免每次统计都全表扫描明细
CREATE TABLE IF NOT EXISTS ai_usage_daily
(
    id           BIGSERIAL PRIMARY KEY,
    stat_date    DATE           NOT NULL,
    user_id      BIGINT         NOT NULL,
    model        VARCHAR(64)    NOT NULL,
    call_count   INTEGER        NOT NULL DEFAULT 0,
    total_tokens BIGINT         NOT NULL DEFAULT 0,
    total_cost   NUMERIC(12, 6) NOT NULL DEFAULT 0,
    fail_count   INTEGER        NOT NULL DEFAULT 0
);

COMMENT ON TABLE ai_usage_daily IS 'AI 用量日聚合，供看板查询';

CREATE UNIQUE INDEX IF NOT EXISTS uk_usage_date_user_model
    ON ai_usage_daily (stat_date, user_id, model);

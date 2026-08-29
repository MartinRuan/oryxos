-- ==========================================================
-- OryxOS 核心持久化表结构 (SQLite)
-- Day One 审计与会话表
-- ==========================================================

-- 1. 会话表 (Sessions)
CREATE TABLE IF NOT EXISTS sessions (
    session_id VARCHAR(64) PRIMARY KEY,
    profile_name VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL DEFAULT 'cli',
    user_id VARCHAR(64),
    messages_json TEXT NOT NULL DEFAULT '[]',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Tool 调用审计表 (Tool Invocations) - Day One 写入
CREATE TABLE IF NOT EXISTS tool_invocations (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    input_json TEXT,
    result_json TEXT,
    success BOOLEAN NOT NULL DEFAULT 1,
    error_message TEXT,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_tool_invocations_session_id ON tool_invocations(session_id);
CREATE INDEX IF NOT EXISTS idx_tool_invocations_tool_name ON tool_invocations(tool_name);

-- 3. LLM 调用审计表 (LLM Calls) - Day One 写入
CREATE TABLE IF NOT EXISTS llm_calls (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model VARCHAR(64) NOT NULL,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_llm_calls_session_id ON llm_calls(session_id);
CREATE INDEX IF NOT EXISTS idx_llm_calls_provider ON llm_calls(provider);

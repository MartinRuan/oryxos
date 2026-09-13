-- ==========================================================
-- OryxOS 核心持久化表结构 (SQLite)
-- 手工建表与演进脚本
-- ==========================================================

-- 1. 会话表 (Sessions)
CREATE TABLE IF NOT EXISTS sessions (
    session_id VARCHAR(128) PRIMARY KEY,
    profile_name VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL DEFAULT 'cli',
    user_id VARCHAR(64),
    messages_json TEXT NOT NULL DEFAULT '[]',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sessions_profile_name ON sessions(profile_name);
CREATE INDEX IF NOT EXISTS idx_sessions_channel_user ON sessions(channel, user_id);

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

-- 3. LLM 调用审计表 (LLM Calls) - Day One 写入 (含成败审计)
CREATE TABLE IF NOT EXISTS llm_calls (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model VARCHAR(64) NOT NULL,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    success BOOLEAN NOT NULL DEFAULT 1,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_llm_calls_session_id ON llm_calls(session_id);
CREATE INDEX IF NOT EXISTS idx_llm_calls_provider ON llm_calls(provider);

-- 4. 定时任务运行状态
CREATE TABLE IF NOT EXISTS scheduled_tasks (
    schedule_id VARCHAR(64) PRIMARY KEY,
    profile_name VARCHAR(64) NOT NULL,
    schedule_key VARCHAR(128) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    cron VARCHAR(128) NOT NULL,
    zone VARCHAR(64) NOT NULL,
    message TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT 1,
    retired BOOLEAN NOT NULL DEFAULT 0,
    next_run_at TIMESTAMP,
    last_run_at TIMESTAMP,
    last_status VARCHAR(32),
    run_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(profile_name, schedule_key)
);
CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_profile ON scheduled_tasks(profile_name);

-- 5. 定时任务执行历史
CREATE TABLE IF NOT EXISTS task_executions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    schedule_id VARCHAR(64),
    session_id VARCHAR(128) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    duration_ms BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_task_executions_schedule ON task_executions(schedule_id);

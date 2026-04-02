CREATE TABLE IF NOT EXISTS ai_configs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_role VARCHAR(20) NOT NULL,
    provider_name VARCHAR(100) NOT NULL,
    api_url VARCHAR(500) NOT NULL,
    api_key_encrypted TEXT NOT NULL,
    api_key_masked VARCHAR(64) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DISABLED',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_configs_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_ai_configs_status
        CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_ai_configs_default_requires_enabled
        CHECK ((NOT is_default) OR status = 'ENABLED'),
    CONSTRAINT uk_ai_configs_user_unique_config
        UNIQUE (user_id, provider_name, api_url, model_name)
);

CREATE INDEX IF NOT EXISTS idx_ai_configs_user_id
    ON ai_configs(user_id);

CREATE INDEX IF NOT EXISTS idx_ai_configs_user_status
    ON ai_configs(user_id, status);

CREATE INDEX IF NOT EXISTS idx_ai_configs_user_updated_at
    ON ai_configs(user_id, updated_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_configs_user_default
    ON ai_configs(user_id)
    WHERE is_default = TRUE;

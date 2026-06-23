CREATE TABLE IF NOT EXISTS video_storage_configs (
    id BIGSERIAL PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL,
    secret_id_encrypted TEXT NOT NULL,
    secret_id_masked VARCHAR(64) NOT NULL,
    secret_key_encrypted TEXT NOT NULL,
    secret_key_masked VARCHAR(64) NOT NULL,
    region VARCHAR(64) NOT NULL,
    sub_app_id BIGINT,
    procedure_name VARCHAR(128),
    status VARCHAR(20) NOT NULL DEFAULT 'DISABLED',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_video_storage_configs_status
        CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_video_storage_configs_default_requires_enabled
        CHECK ((NOT is_default) OR status = 'ENABLED'),
    CONSTRAINT uk_video_storage_configs_name
        UNIQUE (config_name)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_video_storage_configs_default
    ON video_storage_configs(is_default)
    WHERE is_default = TRUE;

CREATE INDEX IF NOT EXISTS idx_video_storage_configs_status
    ON video_storage_configs(status);

CREATE INDEX IF NOT EXISTS idx_video_storage_configs_updated_at
    ON video_storage_configs(updated_at DESC);

CREATE TABLE IF NOT EXISTS video_assets (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120),
    file_size BIGINT NOT NULL,
    tencent_file_id VARCHAR(128) NOT NULL,
    media_url TEXT,
    cover_url TEXT,
    duration_seconds BIGINT,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    created_by BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    scope_type VARCHAR(20) NOT NULL,
    storage_config_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_video_assets_created_by
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_video_assets_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_video_assets_storage_config
        FOREIGN KEY (storage_config_id) REFERENCES video_storage_configs(id) ON DELETE RESTRICT,
    CONSTRAINT ck_video_assets_status
        CHECK (status IN ('PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT ck_video_assets_scope_type
        CHECK (scope_type IN ('SYSTEM', 'TEACHER')),
    CONSTRAINT uk_video_assets_tencent_file_id
        UNIQUE (tencent_file_id)
);

CREATE INDEX IF NOT EXISTS idx_video_assets_created_by
    ON video_assets(created_by);

CREATE INDEX IF NOT EXISTS idx_video_assets_owner_user_id
    ON video_assets(owner_user_id);

CREATE INDEX IF NOT EXISTS idx_video_assets_status
    ON video_assets(status);

CREATE INDEX IF NOT EXISTS idx_video_assets_scope_type
    ON video_assets(scope_type);

CREATE INDEX IF NOT EXISTS idx_video_assets_storage_config_id
    ON video_assets(storage_config_id);

CREATE INDEX IF NOT EXISTS idx_video_assets_updated_at
    ON video_assets(updated_at DESC);

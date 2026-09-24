ALTER TABLE classrooms
    ADD COLUMN IF NOT EXISTS companion_video_id BIGINT,
    ADD COLUMN IF NOT EXISTS companion_image_urls_json JSONB,
    ADD COLUMN IF NOT EXISTS companion_tags_json JSONB;

CREATE INDEX IF NOT EXISTS idx_classrooms_companion_video_id
    ON classrooms(companion_video_id);

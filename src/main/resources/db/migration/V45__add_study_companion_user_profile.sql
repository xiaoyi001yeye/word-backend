ALTER TABLE users
    ADD COLUMN IF NOT EXISTS avatar_key VARCHAR(100),
    ADD COLUMN IF NOT EXISTS gender VARCHAR(20),
    ADD COLUMN IF NOT EXISTS school_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS grade VARCHAR(50),
    ADD COLUMN IF NOT EXISTS interest_tags_json JSONB,
    ADD COLUMN IF NOT EXISTS teaching_stage VARCHAR(50),
    ADD COLUMN IF NOT EXISTS expertise_tags_json JSONB;

COMMENT ON COLUMN users.avatar_key IS '内置头像标识';
COMMENT ON COLUMN users.gender IS 'MALE/FEMALE/UNSPECIFIED';
COMMENT ON COLUMN users.school_name IS '学校名称';
COMMENT ON COLUMN users.grade IS '学生年级编码';
COMMENT ON COLUMN users.interest_tags_json IS '学生兴趣标签 JSON 数组';
COMMENT ON COLUMN users.teaching_stage IS '老师教学学段编码';
COMMENT ON COLUMN users.expertise_tags_json IS '老师擅长领域 JSON 数组';

CREATE TABLE IF NOT EXISTS study_plans (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    teacher_id BIGINT NOT NULL,
    dictionary_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    timezone VARCHAR(64) NOT NULL,
    daily_new_count INT NOT NULL,
    daily_review_limit INT NOT NULL,
    review_mode VARCHAR(32) NOT NULL,
    review_intervals_json TEXT NOT NULL,
    completion_threshold NUMERIC(5, 2) NOT NULL,
    daily_deadline_time TIME NOT NULL,
    attention_tracking_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    min_focus_seconds_per_word INT NOT NULL DEFAULT 3,
    max_focus_seconds_per_word INT NOT NULL DEFAULT 120,
    long_stay_warning_seconds INT NOT NULL DEFAULT 60,
    idle_timeout_seconds INT NOT NULL DEFAULT 15,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_study_plans_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_study_plans_dictionary FOREIGN KEY (dictionary_id) REFERENCES dictionaries(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_study_plans_teacher_id ON study_plans(teacher_id);
CREATE INDEX IF NOT EXISTS idx_study_plans_dictionary_id ON study_plans(dictionary_id);
CREATE INDEX IF NOT EXISTS idx_study_plans_status ON study_plans(status);

CREATE TABLE IF NOT EXISTS study_plan_classrooms (
    id BIGSERIAL PRIMARY KEY,
    study_plan_id BIGINT NOT NULL,
    classroom_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_study_plan_classrooms_plan FOREIGN KEY (study_plan_id) REFERENCES study_plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_study_plan_classrooms_classroom FOREIGN KEY (classroom_id) REFERENCES classrooms(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_study_plan_classrooms_plan_id ON study_plan_classrooms(study_plan_id);
CREATE INDEX IF NOT EXISTS idx_study_plan_classrooms_classroom_id ON study_plan_classrooms(classroom_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_study_plan_classrooms_plan_classroom') THEN
        ALTER TABLE study_plan_classrooms
            ADD CONSTRAINT uk_study_plan_classrooms_plan_classroom UNIQUE (study_plan_id, classroom_id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS student_study_plans (
    id BIGSERIAL PRIMARY KEY,
    study_plan_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    joined_at TIMESTAMP,
    completed_days INT NOT NULL DEFAULT 0,
    missed_days INT NOT NULL DEFAULT 0,
    current_streak INT NOT NULL DEFAULT 0,
    last_study_at TIMESTAMP,
    overall_progress NUMERIC(5, 2) NOT NULL DEFAULT 0,
    avg_focus_seconds NUMERIC(10, 2) NOT NULL DEFAULT 0,
    attention_score NUMERIC(5, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_study_plans_plan FOREIGN KEY (study_plan_id) REFERENCES study_plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_student_study_plans_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_student_study_plans_plan_id ON student_study_plans(study_plan_id);
CREATE INDEX IF NOT EXISTS idx_student_study_plans_student_id ON student_study_plans(student_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_student_study_plans_plan_student') THEN
        ALTER TABLE student_study_plans
            ADD CONSTRAINT uk_student_study_plans_plan_student UNIQUE (study_plan_id, student_id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS study_day_tasks (
    id BIGSERIAL PRIMARY KEY,
    student_study_plan_id BIGINT NOT NULL,
    task_date DATE NOT NULL,
    new_count INT NOT NULL DEFAULT 0,
    review_count INT NOT NULL DEFAULT 0,
    overdue_count INT NOT NULL DEFAULT 0,
    completed_count INT NOT NULL DEFAULT 0,
    completion_rate NUMERIC(5, 2) NOT NULL DEFAULT 0,
    total_focus_seconds INT NOT NULL DEFAULT 0,
    avg_focus_seconds_per_word NUMERIC(10, 2) NOT NULL DEFAULT 0,
    max_focus_seconds_per_word INT NOT NULL DEFAULT 0,
    attention_score NUMERIC(5, 2) NOT NULL DEFAULT 0,
    idle_interrupt_count INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    deadline_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_study_day_tasks_student_plan FOREIGN KEY (student_study_plan_id) REFERENCES student_study_plans(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_study_day_tasks_student_plan_id ON study_day_tasks(student_study_plan_id);
CREATE INDEX IF NOT EXISTS idx_study_day_tasks_task_date ON study_day_tasks(task_date);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_study_day_tasks_plan_date') THEN
        ALTER TABLE study_day_tasks
            ADD CONSTRAINT uk_study_day_tasks_plan_date UNIQUE (student_study_plan_id, task_date);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS study_day_task_items (
    id BIGSERIAL PRIMARY KEY,
    study_day_task_id BIGINT NOT NULL,
    meta_word_id BIGINT NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    task_order INT NOT NULL,
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_study_day_task_items_task FOREIGN KEY (study_day_task_id) REFERENCES study_day_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_study_day_task_items_meta_word FOREIGN KEY (meta_word_id) REFERENCES meta_words(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_study_day_task_items_task_id ON study_day_task_items(study_day_task_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_study_day_task_items_task_word') THEN
        ALTER TABLE study_day_task_items
            ADD CONSTRAINT uk_study_day_task_items_task_word UNIQUE (study_day_task_id, meta_word_id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS study_word_progresses (
    id BIGSERIAL PRIMARY KEY,
    student_study_plan_id BIGINT NOT NULL,
    meta_word_id BIGINT NOT NULL,
    phase INT NOT NULL DEFAULT 0,
    mastery_level NUMERIC(5, 2) NOT NULL DEFAULT 0,
    assigned_date DATE,
    next_review_date DATE,
    last_review_at TIMESTAMP,
    correct_times INT NOT NULL DEFAULT 0,
    wrong_times INT NOT NULL DEFAULT 0,
    total_reviews INT NOT NULL DEFAULT 0,
    last_result VARCHAR(16),
    last_focus_seconds INT,
    avg_focus_seconds NUMERIC(10, 2) NOT NULL DEFAULT 0,
    max_focus_seconds INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT fk_study_word_progresses_student_plan FOREIGN KEY (student_study_plan_id) REFERENCES student_study_plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_study_word_progresses_meta_word FOREIGN KEY (meta_word_id) REFERENCES meta_words(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_study_word_progresses_student_plan_id ON study_word_progresses(student_study_plan_id);
CREATE INDEX IF NOT EXISTS idx_study_word_progresses_next_review_date ON study_word_progresses(next_review_date);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_study_word_progresses_plan_word') THEN
        ALTER TABLE study_word_progresses
            ADD CONSTRAINT uk_study_word_progresses_plan_word UNIQUE (student_study_plan_id, meta_word_id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS study_records (
    id BIGSERIAL PRIMARY KEY,
    student_study_plan_id BIGINT NOT NULL,
    meta_word_id BIGINT NOT NULL,
    task_date DATE NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    result VARCHAR(16) NOT NULL,
    duration_seconds INT,
    focus_seconds INT,
    idle_seconds INT,
    interaction_count INT NOT NULL DEFAULT 0,
    attention_state VARCHAR(32),
    stage_before INT,
    stage_after INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_study_records_student_plan FOREIGN KEY (student_study_plan_id) REFERENCES student_study_plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_study_records_meta_word FOREIGN KEY (meta_word_id) REFERENCES meta_words(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_study_records_plan_date ON study_records(student_study_plan_id, task_date);
CREATE INDEX IF NOT EXISTS idx_study_records_meta_word_id ON study_records(meta_word_id);

CREATE TABLE IF NOT EXISTS student_attention_daily_stats (
    id BIGSERIAL PRIMARY KEY,
    student_study_plan_id BIGINT NOT NULL,
    task_date DATE NOT NULL,
    words_visited INT NOT NULL DEFAULT 0,
    words_completed INT NOT NULL DEFAULT 0,
    total_focus_seconds INT NOT NULL DEFAULT 0,
    avg_focus_seconds_per_word NUMERIC(10, 2) NOT NULL DEFAULT 0,
    median_focus_seconds_per_word NUMERIC(10, 2) NOT NULL DEFAULT 0,
    max_focus_seconds_per_word INT NOT NULL DEFAULT 0,
    long_stay_word_count INT NOT NULL DEFAULT 0,
    idle_interrupt_count INT NOT NULL DEFAULT 0,
    attention_score NUMERIC(5, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_attention_daily_stats_plan FOREIGN KEY (student_study_plan_id) REFERENCES student_study_plans(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_student_attention_daily_stats_plan_id ON student_attention_daily_stats(student_study_plan_id);
CREATE INDEX IF NOT EXISTS idx_student_attention_daily_stats_task_date ON student_attention_daily_stats(task_date);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_student_attention_daily_stats_plan_date') THEN
        ALTER TABLE student_attention_daily_stats
            ADD CONSTRAINT uk_student_attention_daily_stats_plan_date UNIQUE (student_study_plan_id, task_date);
    END IF;
END $$;

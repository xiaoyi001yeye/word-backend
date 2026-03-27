DELETE FROM study_plan_classrooms current_row
USING study_plan_classrooms duplicate_row
WHERE current_row.id > duplicate_row.id
  AND current_row.study_plan_id = duplicate_row.study_plan_id
  AND current_row.classroom_id = duplicate_row.classroom_id;

DROP TABLE IF EXISTS tmp_student_study_plan_merge;
CREATE TEMP TABLE tmp_student_study_plan_merge AS
SELECT duplicate_id, keep_id
FROM (
    SELECT
        id AS duplicate_id,
        FIRST_VALUE(id) OVER (
            PARTITION BY study_plan_id, student_id
            ORDER BY COALESCE(last_study_at, joined_at, created_at) DESC NULLS LAST,
                     overall_progress DESC,
                     attention_score DESC,
                     id ASC
        ) AS keep_id
    FROM student_study_plans
) ranked
WHERE duplicate_id <> keep_id;

UPDATE study_day_tasks current_row
SET student_study_plan_id = merge.keep_id
FROM tmp_student_study_plan_merge merge
WHERE current_row.student_study_plan_id = merge.duplicate_id;

UPDATE study_word_progresses current_row
SET student_study_plan_id = merge.keep_id
FROM tmp_student_study_plan_merge merge
WHERE current_row.student_study_plan_id = merge.duplicate_id;

UPDATE study_records current_row
SET student_study_plan_id = merge.keep_id
FROM tmp_student_study_plan_merge merge
WHERE current_row.student_study_plan_id = merge.duplicate_id;

UPDATE student_attention_daily_stats current_row
SET student_study_plan_id = merge.keep_id
FROM tmp_student_study_plan_merge merge
WHERE current_row.student_study_plan_id = merge.duplicate_id;

DELETE FROM student_study_plans current_row
USING tmp_student_study_plan_merge merge
WHERE current_row.id = merge.duplicate_id;

DROP TABLE IF EXISTS tmp_student_study_plan_merge;

DROP TABLE IF EXISTS tmp_study_day_task_merge;
CREATE TEMP TABLE tmp_study_day_task_merge AS
SELECT duplicate_id, keep_id
FROM (
    SELECT
        id AS duplicate_id,
        FIRST_VALUE(id) OVER (
            PARTITION BY student_study_plan_id, task_date
            ORDER BY completed_count DESC,
                     completion_rate DESC,
                     total_focus_seconds DESC,
                     COALESCE(completed_at, started_at, created_at) DESC NULLS LAST,
                     id ASC
        ) AS keep_id
    FROM study_day_tasks
) ranked
WHERE duplicate_id <> keep_id;

UPDATE study_day_task_items current_row
SET study_day_task_id = merge.keep_id
FROM tmp_study_day_task_merge merge
WHERE current_row.study_day_task_id = merge.duplicate_id;

DELETE FROM study_day_tasks current_row
USING tmp_study_day_task_merge merge
WHERE current_row.id = merge.duplicate_id;

DROP TABLE IF EXISTS tmp_study_day_task_merge;

DROP TABLE IF EXISTS tmp_study_day_task_item_delete;
CREATE TEMP TABLE tmp_study_day_task_item_delete AS
SELECT duplicate_id
FROM (
    SELECT
        id AS duplicate_id,
        ROW_NUMBER() OVER (
            PARTITION BY study_day_task_id, meta_word_id
            ORDER BY (completed_at IS NOT NULL) DESC,
                     completed_at DESC NULLS LAST,
                     task_order ASC,
                     id ASC
        ) AS row_num
    FROM study_day_task_items
) ranked
WHERE row_num > 1;

DELETE FROM study_day_task_items current_row
USING tmp_study_day_task_item_delete duplicate_row
WHERE current_row.id = duplicate_row.duplicate_id;

DROP TABLE IF EXISTS tmp_study_day_task_item_delete;

WITH item_counts AS (
    SELECT
        task.id,
        COUNT(item.id) FILTER (WHERE item.task_type = 'NEW_LEARN')::INT AS new_count,
        COUNT(item.id) FILTER (WHERE item.task_type = 'TODAY_REVIEW')::INT AS review_count,
        COUNT(item.id) FILTER (WHERE item.task_type = 'OVERDUE_REVIEW')::INT AS overdue_count,
        COUNT(item.id) FILTER (WHERE item.completed_at IS NOT NULL)::INT AS completed_count,
        MAX(item.completed_at) AS latest_completed_at
    FROM study_day_tasks task
    LEFT JOIN study_day_task_items item ON item.study_day_task_id = task.id
    GROUP BY task.id
)
UPDATE study_day_tasks current_row
SET new_count = counts.new_count,
    review_count = counts.review_count,
    overdue_count = counts.overdue_count,
    completed_count = counts.completed_count,
    completion_rate = CASE
        WHEN counts.new_count + counts.review_count + counts.overdue_count = 0 THEN 100.00
        ELSE ROUND(
            counts.completed_count * 100.0 / (counts.new_count + counts.review_count + counts.overdue_count),
            2
        )
    END,
    completed_at = CASE
        WHEN counts.new_count + counts.review_count + counts.overdue_count = 0 THEN COALESCE(current_row.completed_at, current_row.created_at)
        WHEN counts.completed_count >= counts.new_count + counts.review_count + counts.overdue_count
            THEN COALESCE(current_row.completed_at, counts.latest_completed_at, current_row.started_at, current_row.created_at)
        ELSE current_row.completed_at
    END,
    status = CASE
        WHEN current_row.status = 'MISSED' THEN 'MISSED'
        WHEN counts.new_count + counts.review_count + counts.overdue_count = 0 THEN 'COMPLETED'
        WHEN counts.completed_count >= counts.new_count + counts.review_count + counts.overdue_count THEN 'COMPLETED'
        WHEN counts.completed_count > 0 THEN 'IN_PROGRESS'
        ELSE 'NOT_STARTED'
    END
FROM item_counts counts
WHERE current_row.id = counts.id;

DROP TABLE IF EXISTS tmp_study_word_progress_delete;
CREATE TEMP TABLE tmp_study_word_progress_delete AS
SELECT duplicate_id
FROM (
    SELECT
        id AS duplicate_id,
        ROW_NUMBER() OVER (
            PARTITION BY student_study_plan_id, meta_word_id
            ORDER BY total_reviews DESC,
                     mastery_level DESC,
                     last_review_at DESC NULLS LAST,
                     id ASC
        ) AS row_num
    FROM study_word_progresses
) ranked
WHERE row_num > 1;

DELETE FROM study_word_progresses current_row
USING tmp_study_word_progress_delete duplicate_row
WHERE current_row.id = duplicate_row.duplicate_id;

DROP TABLE IF EXISTS tmp_study_word_progress_delete;

DROP TABLE IF EXISTS tmp_student_attention_daily_stat_delete;
CREATE TEMP TABLE tmp_student_attention_daily_stat_delete AS
SELECT duplicate_id
FROM (
    SELECT
        id AS duplicate_id,
        ROW_NUMBER() OVER (
            PARTITION BY student_study_plan_id, task_date
            ORDER BY words_completed DESC,
                     total_focus_seconds DESC,
                     created_at DESC NULLS LAST,
                     id ASC
        ) AS row_num
    FROM student_attention_daily_stats
) ranked
WHERE row_num > 1;

DELETE FROM student_attention_daily_stats current_row
USING tmp_student_attention_daily_stat_delete duplicate_row
WHERE current_row.id = duplicate_row.duplicate_id;

DROP TABLE IF EXISTS tmp_student_attention_daily_stat_delete;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_study_plan_classrooms_plan_classroom') THEN
        ALTER TABLE study_plan_classrooms
            ADD CONSTRAINT uk_study_plan_classrooms_plan_classroom UNIQUE (study_plan_id, classroom_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_student_study_plans_plan_student') THEN
        ALTER TABLE student_study_plans
            ADD CONSTRAINT uk_student_study_plans_plan_student UNIQUE (study_plan_id, student_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_study_day_tasks_plan_date') THEN
        ALTER TABLE study_day_tasks
            ADD CONSTRAINT uk_study_day_tasks_plan_date UNIQUE (student_study_plan_id, task_date);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_study_day_task_items_task_word') THEN
        ALTER TABLE study_day_task_items
            ADD CONSTRAINT uk_study_day_task_items_task_word UNIQUE (study_day_task_id, meta_word_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_study_word_progresses_plan_word') THEN
        ALTER TABLE study_word_progresses
            ADD CONSTRAINT uk_study_word_progresses_plan_word UNIQUE (student_study_plan_id, meta_word_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_student_attention_daily_stats_plan_date') THEN
        ALTER TABLE student_attention_daily_stats
            ADD CONSTRAINT uk_student_attention_daily_stats_plan_date UNIQUE (student_study_plan_id, task_date);
    END IF;
END $$;

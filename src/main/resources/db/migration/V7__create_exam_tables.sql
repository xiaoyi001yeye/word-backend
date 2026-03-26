-- Exam table
CREATE SEQUENCE IF NOT EXISTS exams_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS exams (
    id BIGINT DEFAULT nextval('exams_id_seq') PRIMARY KEY,
    dictionary_id BIGINT NOT NULL,
    question_count INT NOT NULL,
    answered_count INT DEFAULT 0,
    correct_count INT DEFAULT 0,
    score INT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'GENERATED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP,
    FOREIGN KEY (dictionary_id) REFERENCES dictionaries(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_exams_dictionary_id ON exams(dictionary_id);
CREATE INDEX IF NOT EXISTS idx_exams_status ON exams(status);

COMMENT ON TABLE exams IS 'Generated exams for dictionary quizzes';
COMMENT ON COLUMN exams.dictionary_id IS 'Foreign key to dictionaries';
COMMENT ON COLUMN exams.question_count IS 'Configured number of questions';
COMMENT ON COLUMN exams.answered_count IS 'Number of answered questions';
COMMENT ON COLUMN exams.correct_count IS 'Number of correct answers';
COMMENT ON COLUMN exams.score IS 'Exam score in percentage';
COMMENT ON COLUMN exams.status IS 'Exam lifecycle status';

-- Exam question table
CREATE SEQUENCE IF NOT EXISTS exam_questions_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS exam_questions (
    id BIGINT DEFAULT nextval('exam_questions_id_seq') PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    meta_word_id BIGINT NOT NULL,
    question_order INT NOT NULL,
    word TEXT NOT NULL,
    option_a TEXT NOT NULL,
    option_b TEXT NOT NULL,
    option_c TEXT NOT NULL,
    option_d TEXT NOT NULL,
    correct_option VARCHAR(1) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
    FOREIGN KEY (meta_word_id) REFERENCES meta_words(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_exam_questions_exam_id ON exam_questions(exam_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_exam_questions_order') THEN
        ALTER TABLE exam_questions ADD CONSTRAINT uk_exam_questions_order UNIQUE (exam_id, question_order);
    END IF;
END $$;

COMMENT ON TABLE exam_questions IS 'Questions that belong to a generated exam';
COMMENT ON COLUMN exam_questions.exam_id IS 'Foreign key to exams';
COMMENT ON COLUMN exam_questions.meta_word_id IS 'Foreign key to meta_words';
COMMENT ON COLUMN exam_questions.question_order IS 'Display order inside the exam';
COMMENT ON COLUMN exam_questions.correct_option IS 'Correct option key: A, B, C or D';

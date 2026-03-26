ALTER TABLE exam_questions
ADD COLUMN IF NOT EXISTS selected_option VARCHAR(1);

COMMENT ON COLUMN exam_questions.selected_option IS 'User selected option key: A, B, C or D';

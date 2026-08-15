ALTER TABLE dictionary_words
    DROP CONSTRAINT IF EXISTS dictionary_words_dictionary_id_fkey;
ALTER TABLE dictionary_words
    ADD CONSTRAINT dictionary_words_dictionary_id_fkey
        FOREIGN KEY (dictionary_id) REFERENCES dictionaries(id) ON DELETE RESTRICT;

ALTER TABLE exams
    DROP CONSTRAINT IF EXISTS exams_dictionary_id_fkey;
ALTER TABLE exams
    ADD CONSTRAINT exams_dictionary_id_fkey
        FOREIGN KEY (dictionary_id) REFERENCES dictionaries(id) ON DELETE RESTRICT;

ALTER TABLE dictionary_assignments
    DROP CONSTRAINT IF EXISTS dictionary_assignments_dictionary_id_fkey;
ALTER TABLE dictionary_assignments
    ADD CONSTRAINT dictionary_assignments_dictionary_id_fkey
        FOREIGN KEY (dictionary_id) REFERENCES dictionaries(id) ON DELETE RESTRICT;

ALTER TABLE study_plans
    DROP CONSTRAINT IF EXISTS fk_study_plans_dictionary;
ALTER TABLE study_plans
    ADD CONSTRAINT fk_study_plans_dictionary
        FOREIGN KEY (dictionary_id) REFERENCES dictionaries(id) ON DELETE RESTRICT;

ALTER TABLE tags
    DROP CONSTRAINT IF EXISTS fk_tags_dictionary;
ALTER TABLE tags
    ADD CONSTRAINT fk_tags_dictionary
        FOREIGN KEY (dictionary_id) REFERENCES dictionaries(id) ON DELETE RESTRICT;

ALTER TABLE classroom_dictionary_assignments
    DROP CONSTRAINT IF EXISTS fk_classroom_dictionary_assignments_dictionary;
ALTER TABLE classroom_dictionary_assignments
    ADD CONSTRAINT fk_classroom_dictionary_assignments_dictionary
        FOREIGN KEY (dictionary_id) REFERENCES dictionaries(id) ON DELETE RESTRICT;

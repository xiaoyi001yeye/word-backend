CREATE SEQUENCE IF NOT EXISTS classroom_dictionary_assignments_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS classroom_dictionary_assignments (
    id BIGINT DEFAULT nextval('classroom_dictionary_assignments_id_seq') PRIMARY KEY,
    classroom_id BIGINT NOT NULL,
    dictionary_id BIGINT NOT NULL,
    assigned_by_user_id BIGINT NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_classroom_dictionary_assignments_classroom
        FOREIGN KEY (classroom_id) REFERENCES classrooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_classroom_dictionary_assignments_dictionary
        FOREIGN KEY (dictionary_id) REFERENCES dictionaries(id) ON DELETE CASCADE,
    CONSTRAINT uk_classroom_dictionary_assignment UNIQUE (classroom_id, dictionary_id)
);

CREATE INDEX IF NOT EXISTS idx_classroom_dictionary_assignments_classroom_id
    ON classroom_dictionary_assignments(classroom_id);

CREATE INDEX IF NOT EXISTS idx_classroom_dictionary_assignments_dictionary_id
    ON classroom_dictionary_assignments(dictionary_id);

CREATE SEQUENCE IF NOT EXISTS users_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT DEFAULT nextval('users_id_seq') PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(50),
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_users_username') THEN
        ALTER TABLE users ADD CONSTRAINT uk_users_username UNIQUE (username);
    END IF;
END $$;

ALTER TABLE dictionaries
    ADD COLUMN IF NOT EXISTS created_by BIGINT,
    ADD COLUMN IF NOT EXISTS owner_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS scope_type VARCHAR(20) NOT NULL DEFAULT 'SYSTEM';

CREATE INDEX IF NOT EXISTS idx_dictionaries_created_by ON dictionaries(created_by);
CREATE INDEX IF NOT EXISTS idx_dictionaries_owner_user_id ON dictionaries(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_dictionaries_scope_type ON dictionaries(scope_type);

ALTER TABLE exams
    ADD COLUMN IF NOT EXISTS created_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS target_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_exams_created_by_user_id ON exams(created_by_user_id);
CREATE INDEX IF NOT EXISTS idx_exams_target_user_id ON exams(target_user_id);

CREATE SEQUENCE IF NOT EXISTS teacher_student_relations_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS teacher_student_relations (
    id BIGINT DEFAULT nextval('teacher_student_relations_id_seq') PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_teacher_student_relations_teacher_id ON teacher_student_relations(teacher_id);
CREATE INDEX IF NOT EXISTS idx_teacher_student_relations_student_id ON teacher_student_relations(student_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_teacher_student_relation') THEN
        ALTER TABLE teacher_student_relations
            ADD CONSTRAINT uk_teacher_student_relation UNIQUE (teacher_id, student_id);
    END IF;
END $$;

CREATE SEQUENCE IF NOT EXISTS dictionary_assignments_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS dictionary_assignments (
    id BIGINT DEFAULT nextval('dictionary_assignments_id_seq') PRIMARY KEY,
    dictionary_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    assigned_by_user_id BIGINT NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (dictionary_id) REFERENCES dictionaries(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (assigned_by_user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_dictionary_assignments_dictionary_id ON dictionary_assignments(dictionary_id);
CREATE INDEX IF NOT EXISTS idx_dictionary_assignments_student_id ON dictionary_assignments(student_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_dictionary_assignment') THEN
        ALTER TABLE dictionary_assignments
            ADD CONSTRAINT uk_dictionary_assignment UNIQUE (dictionary_id, student_id);
    END IF;
END $$;

INSERT INTO users (username, password_hash, display_name, role, status)
SELECT
    'admin',
    '$2y$10$.oP9Ne5nlNzbdHykuuzNz.Z3/2xzh94bEbpHlHxuaWn7.Egx5i9T2',
    'System Admin',
    'ADMIN',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'admin'
);

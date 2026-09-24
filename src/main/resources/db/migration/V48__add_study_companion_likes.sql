CREATE TABLE IF NOT EXISTS classroom_companion_likes (
    id BIGSERIAL PRIMARY KEY,
    classroom_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_classroom_companion_like UNIQUE (classroom_id, user_id),
    CONSTRAINT fk_companion_like_classroom FOREIGN KEY (classroom_id) REFERENCES classrooms(id),
    CONSTRAINT fk_companion_like_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_companion_like_classroom_created
    ON classroom_companion_likes (classroom_id, created_at DESC);

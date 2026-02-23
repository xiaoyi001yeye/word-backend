-- Add creation_type column to dictionaries table
ALTER TABLE dictionaries ADD COLUMN IF NOT EXISTS creation_type VARCHAR(20) NOT NULL DEFAULT 'USER_CREATED';

COMMENT ON COLUMN dictionaries.creation_type IS 'Creation type: USER_CREATED or IMPORTED';

-- Update existing records to have USER_CREATED as default
UPDATE dictionaries SET creation_type = 'USER_CREATED' WHERE creation_type IS NULL;
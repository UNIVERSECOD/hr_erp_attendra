-- Add first_name and last_name to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS first_name VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_name VARCHAR(100);

-- Columns are nullable to preserve backward compatibility for existing rows.
-- New signups will require these values via application-level validation in SignupRequest.

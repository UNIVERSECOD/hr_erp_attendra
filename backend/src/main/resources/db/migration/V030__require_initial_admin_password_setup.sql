ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_setup_required BOOLEAN NOT NULL DEFAULT FALSE;

-- Only the untouched seeded account is moved into first-run setup. Existing
-- installations that already changed the admin password remain unaffected.
UPDATE users
SET password_setup_required = TRUE
WHERE username = 'admin'
  AND password_hash = '$2a$12$tsWFUh9Wahb8CFGeVCIR.uSHAQomV.a4Gjfef3WK/UT.XznSvMUQ6';

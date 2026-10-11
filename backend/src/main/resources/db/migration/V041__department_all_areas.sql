-- Existing departments retain their area and all employee/schedule references.
ALTER TABLE departments ADD COLUMN all_areas BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE departments ADD COLUMN IF NOT EXISTS calculate_overtime BOOLEAN DEFAULT FALSE,
                        ADD COLUMN IF NOT EXISTS flex_shift BOOLEAN DEFAULT FALSE,
                        ADD COLUMN IF NOT EXISTS timetable VARCHAR(255);

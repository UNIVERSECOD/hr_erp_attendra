ALTER TABLE timetable_day_rules
    ALTER COLUMN day_of_week TYPE INTEGER
    USING day_of_week::INTEGER;

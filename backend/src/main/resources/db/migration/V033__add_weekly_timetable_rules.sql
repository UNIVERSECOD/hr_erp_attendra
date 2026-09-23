CREATE TABLE IF NOT EXISTS timetable_day_rules (
    id BIGSERIAL PRIMARY KEY,
    timetable_id BIGINT NOT NULL REFERENCES timetables(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL,
    working_day BOOLEAN NOT NULL DEFAULT TRUE,
    start_time TIME,
    end_time TIME,
    break_minutes INT NOT NULL DEFAULT 0,
    allowed_late_minutes INT NOT NULL DEFAULT 0,
    allowed_early_leave_minutes INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_timetable_day_rules_day UNIQUE (timetable_id, day_of_week),
    CONSTRAINT chk_timetable_day_rules_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_timetable_day_rules_break CHECK (break_minutes >= 0),
    CONSTRAINT chk_timetable_day_rules_late CHECK (allowed_late_minutes >= 0),
    CONSTRAINT chk_timetable_day_rules_early CHECK (allowed_early_leave_minutes >= 0)
);

CREATE INDEX IF NOT EXISTS idx_timetable_day_rules_timetable
    ON timetable_day_rules(timetable_id);

-- Preserve legacy behavior exactly: existing single-range timetables applied on every day.
-- Administrators can then mark rest days explicitly in the new weekly editor.
INSERT INTO timetable_day_rules (
    timetable_id,
    day_of_week,
    working_day,
    start_time,
    end_time,
    break_minutes,
    allowed_late_minutes,
    allowed_early_leave_minutes
)
SELECT
    timetable.id,
    day_number,
    TRUE,
    timetable.start_time,
    timetable.end_time,
    COALESCE(timetable.break_minutes, 0),
    COALESCE(timetable.allowed_late_minutes, 0),
    COALESCE(timetable.allowed_early_leave_minutes, 0)
FROM timetables timetable
CROSS JOIN generate_series(1, 7) AS day_number
ON CONFLICT (timetable_id, day_of_week) DO NOTHING;

ALTER TABLE daily_attendance_summaries
    ADD COLUMN IF NOT EXISTS late_minutes INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS early_leave_minutes INT NOT NULL DEFAULT 0;

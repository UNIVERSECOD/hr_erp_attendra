-- Freeze schedule classification on attendance sessions so later timetable changes
-- cannot reclassify or collapse historical punches.
ALTER TABLE attendance_logs
    ADD COLUMN IF NOT EXISTS shift_type VARCHAR(50);

ALTER TABLE attendance_logs
    ADD COLUMN IF NOT EXISTS timetable_id BIGINT;

-- Backfill from ACTIVE assignment covering the check-in calendar day.
UPDATE attendance_logs al
SET shift_type = sub.shift_type,
    timetable_id = sub.timetable_id
FROM (
    SELECT DISTINCT ON (al2.id)
        al2.id AS log_id,
        esa.timetable_id,
        t.shift_type
    FROM attendance_logs al2
    JOIN employee_shift_assignments esa
      ON esa.employee_id = al2.employee_id
     AND esa.tenant_id = al2.tenant_id
     AND esa.status = 'ACTIVE'
     AND al2.check_in_time IS NOT NULL
     AND esa.effective_start_date <= CAST(al2.check_in_time AS DATE)
     AND (esa.effective_end_date IS NULL OR esa.effective_end_date >= CAST(al2.check_in_time AS DATE))
    JOIN timetables t ON t.id = esa.timetable_id
    WHERE al2.shift_type IS NULL
    ORDER BY al2.id, esa.effective_start_date DESC, esa.id DESC
) sub
WHERE al.id = sub.log_id
  AND al.shift_type IS NULL;

CREATE INDEX IF NOT EXISTS idx_attendance_logs_shift_type ON attendance_logs(shift_type);

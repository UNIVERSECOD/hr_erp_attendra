ALTER TABLE device_configs ADD COLUMN attendance_role_since TIMESTAMP;
ALTER TABLE attendance_logs ADD COLUMN combined_area_id BIGINT;
ALTER TABLE attendance_logs ADD COLUMN combined_work_date DATE;

CREATE UNIQUE INDEX uq_attendance_combined_day
    ON attendance_logs (tenant_id, employee_id, combined_area_id, combined_work_date)
    WHERE combined_area_id IS NOT NULL;

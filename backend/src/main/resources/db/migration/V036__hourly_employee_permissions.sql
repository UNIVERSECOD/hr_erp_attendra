ALTER TABLE employee_permissions
    ADD COLUMN IF NOT EXISTS start_time TIME,
    ADD COLUMN IF NOT EXISTS end_time TIME,
    ADD COLUMN IF NOT EXISTS deduct_from_work_hours BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE attendance_records
    ADD COLUMN IF NOT EXISTS permission_minutes INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS credited_permission_minutes INT NOT NULL DEFAULT 0;

ALTER TABLE daily_attendance_summaries
    ADD COLUMN IF NOT EXISTS permission_minutes INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS credited_permission_minutes INT NOT NULL DEFAULT 0;

ALTER TABLE daily_attendance_summaries
    ALTER COLUMN attendance_status TYPE VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_employee_permissions_active_range
    ON employee_permissions (tenant_id, employee_id, start_date, end_date, status);

INSERT INTO permission_types (tenant_id, code, name, is_custom)
SELECT tenant.id, 'HOURLY_PERMISSION', 'Saatlıq icazə', FALSE
FROM tenants tenant
WHERE NOT EXISTS (
    SELECT 1
    FROM permission_types permission_type
    WHERE permission_type.tenant_id = tenant.id
      AND permission_type.code = 'HOURLY_PERMISSION'
);

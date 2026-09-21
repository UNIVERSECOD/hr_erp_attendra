-- Device person ID (raw Hikvision employeeNo) kept separate from prefixed HR employee_id.
-- Example: employee_id = 'BAK-1001', device_employee_no = '1001'
ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS device_employee_no VARCHAR(64);

UPDATE employees
SET device_employee_no = employee_id
WHERE device_employee_no IS NULL
  AND employee_id IS NOT NULL
  AND employee_id <> '';

CREATE INDEX IF NOT EXISTS idx_employees_tenant_device_employee_no
    ON employees (tenant_id, device_employee_no);

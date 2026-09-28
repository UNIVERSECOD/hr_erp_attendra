CREATE TABLE IF NOT EXISTS employee_areas (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_employee_areas_employee_branch UNIQUE (employee_id, branch_id)
);

CREATE INDEX IF NOT EXISTS idx_employee_areas_tenant ON employee_areas(tenant_id);
CREATE INDEX IF NOT EXISTS idx_employee_areas_employee ON employee_areas(employee_id);
CREATE INDEX IF NOT EXISTS idx_employee_areas_branch ON employee_areas(branch_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_employee_areas_primary
    ON employee_areas(employee_id)
    WHERE is_primary = TRUE;

-- Preserve the existing primary area while enabling additional area memberships.
INSERT INTO employee_areas (tenant_id, employee_id, branch_id, is_primary)
SELECT e.tenant_id, e.id, e.branch_id, TRUE
FROM employees e
WHERE e.branch_id IS NOT NULL
ON CONFLICT (employee_id, branch_id) DO NOTHING;

ALTER TABLE employee_device_access
    ADD COLUMN IF NOT EXISTS assignment_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE employee_device_access
    ADD COLUMN IF NOT EXISTS source_branch_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_employee_device_access_source_branch
    ON employee_device_access(source_branch_id);

-- Existing same-area links were created automatically by the employee flow.
-- Cross-area links stay MANUAL so the migration never removes explicit access.
UPDATE employee_device_access access
SET assignment_source = 'AREA',
    source_branch_id = device.branch_id
FROM employees employee,
     device_configs device
WHERE access.employee_id = employee.id
  AND access.device_config_id = device.id
  AND employee.branch_id IS NOT NULL
  AND device.branch_id = employee.branch_id
  AND access.assignment_source = 'MANUAL'
  AND access.source_branch_id IS NULL;

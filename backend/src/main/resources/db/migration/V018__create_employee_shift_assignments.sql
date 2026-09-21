CREATE TABLE IF NOT EXISTS employee_shift_assignments (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    timetable_id BIGINT NOT NULL REFERENCES timetables(id) ON DELETE CASCADE,
    effective_start_date DATE NOT NULL,
    effective_end_date DATE,
    assigned_by BIGINT,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);

CREATE INDEX IF NOT EXISTS idx_shift_assignments_tenant ON employee_shift_assignments(tenant_id);
CREATE INDEX IF NOT EXISTS idx_shift_assignments_employee ON employee_shift_assignments(employee_id);
CREATE INDEX IF NOT EXISTS idx_shift_assignments_timetable ON employee_shift_assignments(timetable_id);
CREATE INDEX IF NOT EXISTS idx_shift_assignments_dates ON employee_shift_assignments(effective_start_date, effective_end_date);

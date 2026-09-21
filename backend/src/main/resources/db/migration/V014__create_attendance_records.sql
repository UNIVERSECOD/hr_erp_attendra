CREATE TABLE attendance_records (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    work_date DATE NOT NULL,
    entry_time TIMESTAMP,
    exit_time TIMESTAMP,
    worked_minutes INT NOT NULL DEFAULT 0,
    overtime_minutes INT NOT NULL DEFAULT 0,
    late_minutes INT NOT NULL DEFAULT 0,
    early_leave_minutes INT NOT NULL DEFAULT 0,
    timetable_id BIGINT,
    shift_type VARCHAR(20),
    status VARCHAR(30) NOT NULL DEFAULT 'NO_DATA',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (employee_id, work_date)
);

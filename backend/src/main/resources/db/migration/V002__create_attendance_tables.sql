-- Attendance Logs
CREATE TABLE IF NOT EXISTS attendance_logs (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id),
    check_in_time TIMESTAMP,
    check_out_time TIMESTAMP,
    device_id VARCHAR(100),
    door_id VARCHAR(100),
    event_type VARCHAR(50),
    verification_method VARCHAR(50),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Daily Attendance Summaries
CREATE TABLE IF NOT EXISTS daily_attendance_summaries (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id),
    attendance_date DATE NOT NULL,
    check_in_time TIMESTAMP,
    check_out_time TIMESTAMP,
    hours_worked DOUBLE PRECISION DEFAULT 0,
    is_standard_day BOOLEAN DEFAULT TRUE,
    is_additional_day BOOLEAN DEFAULT FALSE,
    is_extra_day BOOLEAN DEFAULT FALSE,
    is_holiday BOOLEAN DEFAULT FALSE,
    is_leave BOOLEAN DEFAULT FALSE,
    attendance_status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (employee_id, attendance_date)
);

CREATE INDEX IF NOT EXISTS idx_attendance_logs_employee ON attendance_logs(employee_id);
CREATE INDEX IF NOT EXISTS idx_attendance_logs_checkin ON attendance_logs(check_in_time);
CREATE INDEX IF NOT EXISTS idx_daily_summaries_employee_date ON daily_attendance_summaries(employee_id, attendance_date);

-- Add CHECK constraint: leave end_date must be >= start_date
ALTER TABLE leave_requests
    ADD CONSTRAINT chk_leave_dates CHECK (end_date >= start_date);

-- Add composite index on leave_requests for date range queries
CREATE INDEX IF NOT EXISTS idx_leave_requests_employee_dates
    ON leave_requests(employee_id, start_date, end_date);

-- Add index on leave_requests status for filtering
CREATE INDEX IF NOT EXISTS idx_leave_requests_status
    ON leave_requests(status);

-- Add index on attendance_logs for date range queries
CREATE INDEX IF NOT EXISTS idx_attendance_logs_employee_date
    ON attendance_logs(employee_id, check_in_time);

-- Add index on daily_attendance_summary for date range queries
CREATE INDEX IF NOT EXISTS idx_daily_summary_employee_date
    ON daily_attendance_summaries(employee_id, attendance_date);

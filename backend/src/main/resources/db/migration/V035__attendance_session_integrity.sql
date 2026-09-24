ALTER TABLE attendance_logs
    ADD COLUMN IF NOT EXISTS entry_punch_id BIGINT,
    ADD COLUMN IF NOT EXISTS exit_punch_id BIGINT,
    ADD COLUMN IF NOT EXISTS manual_override BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uq_attendance_logs_tenant_entry_punch
    ON attendance_logs (tenant_id, entry_punch_id)
    WHERE entry_punch_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_attendance_logs_tenant_exit_punch
    ON attendance_logs (tenant_id, exit_punch_id)
    WHERE exit_punch_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_attendance_logs_open_sessions
    ON attendance_logs (tenant_id, employee_id, check_in_time DESC)
    WHERE check_out_time IS NULL;

CREATE TABLE IF NOT EXISTS attendance_log_adjustments (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    attendance_log_id BIGINT NOT NULL REFERENCES attendance_logs(id) ON DELETE CASCADE,
    previous_check_in_time TIMESTAMP,
    previous_check_out_time TIMESTAMP,
    new_check_in_time TIMESTAMP NOT NULL,
    new_check_out_time TIMESTAMP,
    reason VARCHAR(500) NOT NULL,
    corrected_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_attendance_log_adjustments_log
    ON attendance_log_adjustments (tenant_id, attendance_log_id, created_at DESC);

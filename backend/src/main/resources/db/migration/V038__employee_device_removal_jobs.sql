CREATE TABLE employee_device_removal_jobs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    employee_id BIGINT NOT NULL REFERENCES employees(id),
    device_config_id BIGINT NOT NULL,
    bridge_device_id VARCHAR(255),
    device_ip VARCHAR(255),
    device_name VARCHAR(255),
    employee_no VARCHAR(255) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    UNIQUE (tenant_id, employee_id, device_config_id)
);
CREATE INDEX idx_removal_jobs_due ON employee_device_removal_jobs(tenant_id, next_attempt_at) WHERE completed = FALSE;

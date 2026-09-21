CREATE TABLE IF NOT EXISTS employee_device_access (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    device_config_id BIGINT NOT NULL REFERENCES device_configs(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_employee_device_access_employee_device UNIQUE (employee_id, device_config_id)
);

CREATE INDEX IF NOT EXISTS idx_employee_device_access_employee ON employee_device_access(employee_id);
CREATE INDEX IF NOT EXISTS idx_employee_device_access_device ON employee_device_access(device_config_id);
CREATE INDEX IF NOT EXISTS idx_employee_device_access_tenant ON employee_device_access(tenant_id);

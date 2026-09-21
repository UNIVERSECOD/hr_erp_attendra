-- Tenants table (one row per company/client)
CREATE TABLE IF NOT EXISTS tenants (
    id BIGSERIAL PRIMARY KEY,
    tenant_code VARCHAR(50) NOT NULL UNIQUE,
    company_name VARCHAR(200) NOT NULL,
    subscription_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    contact_email VARCHAR(200),
    contact_phone VARCHAR(50),
    max_employees INTEGER DEFAULT 1000,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert default tenant for existing data
INSERT INTO tenants (tenant_code, company_name, subscription_status, contact_email)
VALUES ('DEFAULT', 'Default Company', 'ACTIVE', 'admin@hic.az')
ON CONFLICT (tenant_code) DO NOTHING;

-- Add tenant_id to branches
ALTER TABLE branches ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
UPDATE branches SET tenant_id = (SELECT id FROM tenants WHERE tenant_code = 'DEFAULT') WHERE tenant_id IS NULL;
ALTER TABLE branches ALTER COLUMN tenant_id SET NOT NULL;

-- Add tenant_id to departments (inherited via branch but also directly for fast filtering)
ALTER TABLE departments ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
UPDATE departments SET tenant_id = (SELECT id FROM tenants WHERE tenant_code = 'DEFAULT') WHERE tenant_id IS NULL;
ALTER TABLE departments ALTER COLUMN tenant_id SET NOT NULL;

-- Add tenant_id to positions
ALTER TABLE positions ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
UPDATE positions SET tenant_id = (SELECT id FROM tenants WHERE tenant_code = 'DEFAULT') WHERE tenant_id IS NULL;
ALTER TABLE positions ALTER COLUMN tenant_id SET NOT NULL;

-- Add tenant_id to employees
ALTER TABLE employees ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
UPDATE employees SET tenant_id = (SELECT id FROM tenants WHERE tenant_code = 'DEFAULT') WHERE tenant_id IS NULL;
ALTER TABLE employees ALTER COLUMN tenant_id SET NOT NULL;

-- Add tenant_id to users
ALTER TABLE users ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
UPDATE users SET tenant_id = (SELECT id FROM tenants WHERE tenant_code = 'DEFAULT') WHERE tenant_id IS NULL;
ALTER TABLE users ALTER COLUMN tenant_id SET NOT NULL;

-- Add tenant_id to leave_types (shared across tenant or tenant-specific)
ALTER TABLE leave_types ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
UPDATE leave_types SET tenant_id = (SELECT id FROM tenants WHERE tenant_code = 'DEFAULT') WHERE tenant_id IS NULL;
ALTER TABLE leave_types ALTER COLUMN tenant_id SET NOT NULL;

-- Drop old unique constraint and add tenant-scoped unique constraint on leave_code
ALTER TABLE leave_types DROP CONSTRAINT IF EXISTS leave_types_leave_code_key;
ALTER TABLE leave_types ADD CONSTRAINT uq_leave_types_tenant_code UNIQUE (tenant_id, leave_code);

-- Add tenant_id to leave_requests
ALTER TABLE leave_requests ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
UPDATE leave_requests SET tenant_id = (SELECT id FROM tenants WHERE tenant_code = 'DEFAULT') WHERE tenant_id IS NULL;
ALTER TABLE leave_requests ALTER COLUMN tenant_id SET NOT NULL;

-- Add tenant_id to attendance_logs
ALTER TABLE attendance_logs ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
UPDATE attendance_logs SET tenant_id = (SELECT id FROM tenants WHERE tenant_code = 'DEFAULT') WHERE tenant_id IS NULL;
ALTER TABLE attendance_logs ALTER COLUMN tenant_id SET NOT NULL;

-- Add tenant_id to daily_attendance_summaries
ALTER TABLE daily_attendance_summaries ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
UPDATE daily_attendance_summaries SET tenant_id = (SELECT id FROM tenants WHERE tenant_code = 'DEFAULT') WHERE tenant_id IS NULL;
ALTER TABLE daily_attendance_summaries ALTER COLUMN tenant_id SET NOT NULL;

-- Add tenant_id to device_configs
ALTER TABLE device_configs ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
UPDATE device_configs SET tenant_id = (SELECT id FROM tenants WHERE tenant_code = 'DEFAULT') WHERE tenant_id IS NULL;

-- Drop old unique constraint and add tenant-scoped unique constraint on device_id
ALTER TABLE device_configs DROP CONSTRAINT IF EXISTS device_configs_device_id_key;
ALTER TABLE device_configs ADD CONSTRAINT uq_device_configs_tenant_device UNIQUE (tenant_id, device_id);

-- Add tenant_id to work_schedules
ALTER TABLE work_schedules ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
UPDATE work_schedules SET tenant_id = (SELECT id FROM tenants WHERE tenant_code = 'DEFAULT') WHERE tenant_id IS NULL;

-- Remove old single-tenant unique constraint on employees.employee_id and add tenant-scoped one
ALTER TABLE employees DROP CONSTRAINT IF EXISTS employees_employee_id_key;
ALTER TABLE employees ADD CONSTRAINT uq_employees_tenant_employee_id UNIQUE (tenant_id, employee_id);

-- Remove old single-tenant unique constraint on employees.fin_number and add tenant-scoped one
ALTER TABLE employees DROP CONSTRAINT IF EXISTS employees_fin_number_key;
ALTER TABLE employees ADD CONSTRAINT uq_employees_tenant_fin_number UNIQUE (tenant_id, fin_number);

-- Performance indexes for tenant filtering
CREATE INDEX IF NOT EXISTS idx_employees_tenant ON employees(tenant_id);
CREATE INDEX IF NOT EXISTS idx_users_tenant ON users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_branches_tenant ON branches(tenant_id);
CREATE INDEX IF NOT EXISTS idx_departments_tenant ON departments(tenant_id);
CREATE INDEX IF NOT EXISTS idx_leave_requests_tenant ON leave_requests(tenant_id);
CREATE INDEX IF NOT EXISTS idx_attendance_logs_tenant ON attendance_logs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_daily_summaries_tenant ON daily_attendance_summaries(tenant_id);
CREATE INDEX IF NOT EXISTS idx_device_configs_tenant ON device_configs(tenant_id);

-- Audit log table
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT REFERENCES tenants(id),
    user_id BIGINT,
    username VARCHAR(100),
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(100),
    entity_id VARCHAR(100),
    details TEXT,
    ip_address VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant ON audit_logs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs(created_at);

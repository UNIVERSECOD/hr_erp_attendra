CREATE TABLE IF NOT EXISTS holiday_permissions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    apply_scope VARCHAR(30) NOT NULL DEFAULT 'COMPANY',
    target_ids BIGINT[],
    employee_ids BIGINT[],
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_holiday_permissions_dates CHECK (end_date >= start_date)
);

CREATE INDEX IF NOT EXISTS idx_holiday_permissions_tenant_date
    ON holiday_permissions(tenant_id, start_date, end_date);

CREATE TABLE IF NOT EXISTS annual_leave_balances (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    year INT NOT NULL,
    entitlement_days INT NOT NULL DEFAULT 0,
    used_days INT NOT NULL DEFAULT 0,
    remaining_days INT NOT NULL DEFAULT 0,
    carryover_days INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_annual_leave_balance UNIQUE (tenant_id, employee_id, year),
    CONSTRAINT chk_annual_leave_non_negative CHECK (
        entitlement_days >= 0
        AND used_days >= 0
        AND remaining_days >= 0
        AND carryover_days >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_annual_leave_tenant_year
    ON annual_leave_balances(tenant_id, year);
CREATE INDEX IF NOT EXISTS idx_annual_leave_tenant_employee
    ON annual_leave_balances(tenant_id, employee_id);

CREATE TABLE leaves (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    leave_type VARCHAR(100) NOT NULL,
    apply_type VARCHAR(20) NOT NULL DEFAULT 'EMPLOYEE',
    target_id BIGINT,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Seed data removed: leaves are created by administrators via the UI.

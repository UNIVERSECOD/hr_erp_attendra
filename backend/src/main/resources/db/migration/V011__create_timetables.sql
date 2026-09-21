CREATE TABLE timetables (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    crosses_midnight BOOLEAN NOT NULL DEFAULT FALSE,
    allowed_late_minutes INT NOT NULL DEFAULT 0,
    allowed_early_leave_minutes INT NOT NULL DEFAULT 0,
    shift_type VARCHAR(20) NOT NULL DEFAULT 'STANDART',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Seed data removed: timetables are created by administrators via the UI.

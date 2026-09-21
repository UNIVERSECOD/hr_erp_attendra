-- Leave Types
CREATE TABLE IF NOT EXISTS leave_types (
    id BIGSERIAL PRIMARY KEY,
    leave_code VARCHAR(50) NOT NULL UNIQUE,
    leave_name VARCHAR(200) NOT NULL,
    annual_entitlement INTEGER,
    is_paid BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Leave Requests
CREATE TABLE IF NOT EXISTS leave_requests (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id),
    leave_type_id BIGINT NOT NULL REFERENCES leave_types(id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_by BIGINT REFERENCES users(id),
    approval_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Face Data
CREATE TABLE IF NOT EXISTS face_data (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id),
    face_id VARCHAR(100),
    face_image_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Access Cards
CREATE TABLE IF NOT EXISTS access_cards (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id),
    card_number VARCHAR(100) NOT NULL,
    card_type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Device Configs
CREATE TABLE IF NOT EXISTS device_configs (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(100) NOT NULL UNIQUE,
    device_name VARCHAR(200),
    device_ip VARCHAR(50) NOT NULL,
    device_port INTEGER DEFAULT 80,
    username VARCHAR(100),
    password_encrypted VARCHAR(255),
    branch_id BIGINT REFERENCES branches(id),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    last_sync_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Device Sync Histories
CREATE TABLE IF NOT EXISTS device_sync_histories (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(100) NOT NULL,
    sync_start_time TIMESTAMP,
    sync_end_time TIMESTAMP,
    records_synced INTEGER DEFAULT 0,
    sync_status VARCHAR(20),
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_leave_requests_employee ON leave_requests(employee_id);
CREATE INDEX IF NOT EXISTS idx_leave_requests_status ON leave_requests(status);
CREATE INDEX IF NOT EXISTS idx_device_sync_histories_device ON device_sync_histories(device_id);

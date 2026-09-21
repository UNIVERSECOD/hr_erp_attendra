-- Create device_users table
CREATE TABLE IF NOT EXISTS device_users (
    id BIGSERIAL PRIMARY KEY,
    employee_no VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    user_type VARCHAR(50) NOT NULL,
    gender VARCHAR(20),
    begin_time TIMESTAMP,
    end_time TIMESTAMP,
    face_data_url TEXT,
    synced_to_device BOOLEAN NOT NULL DEFAULT FALSE,
    last_sync_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_device_user_employee_no UNIQUE (employee_no)
);

CREATE INDEX IF NOT EXISTS idx_device_users_employee_no ON device_users(employee_no);
CREATE INDEX IF NOT EXISTS idx_device_users_synced ON device_users(synced_to_device);

-- Create devices table
CREATE TABLE IF NOT EXISTS devices (
    id BIGSERIAL PRIMARY KEY,
    ip VARCHAR(45) NOT NULL,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_devices_ip ON devices(ip);
CREATE INDEX IF NOT EXISTS idx_devices_enabled ON devices(enabled);

-- Create acs_raw_events table
CREATE TABLE IF NOT EXISTS acs_raw_events (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    serial_no BIGINT,
    event_time TIMESTAMP WITH TIME ZONE,
    major_event_type INTEGER,
    sub_event_type INTEGER,
    employee_no_string VARCHAR(255),
    card_no VARCHAR(255),
    raw_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_acs_raw_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    CONSTRAINT uq_acs_raw_device_serial UNIQUE (device_id, serial_no)
);

CREATE INDEX IF NOT EXISTS idx_acs_raw_device ON acs_raw_events(device_id);
CREATE INDEX IF NOT EXISTS idx_acs_raw_event_time ON acs_raw_events(event_time);

-- Create attendance_punches table
CREATE TABLE IF NOT EXISTS attendance_punches (
    id BIGSERIAL PRIMARY KEY,
    raw_event_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    employee_no VARCHAR(50),
    punch_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attendance_punches_raw_event FOREIGN KEY (raw_event_id) REFERENCES acs_raw_events(id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_punches_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_attendance_punches_device ON attendance_punches(device_id);
CREATE INDEX IF NOT EXISTS idx_attendance_punches_employee ON attendance_punches(employee_no);
CREATE INDEX IF NOT EXISTS idx_attendance_punches_time ON attendance_punches(punch_time);

-- Create acs_failed_attempts table
CREATE TABLE IF NOT EXISTS acs_failed_attempts (
    id BIGSERIAL PRIMARY KEY,
    raw_event_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    identity VARCHAR(255),
    sub_event_type INTEGER,
    event_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_acs_failed_raw_event FOREIGN KEY (raw_event_id) REFERENCES acs_raw_events(id) ON DELETE CASCADE,
    CONSTRAINT fk_acs_failed_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_acs_failed_device ON acs_failed_attempts(device_id);
CREATE INDEX IF NOT EXISTS idx_acs_failed_event_time ON acs_failed_attempts(event_time);

-- Create device_cursors table
CREATE TABLE IF NOT EXISTS device_cursors (
    device_id BIGINT PRIMARY KEY,
    last_serial_no BIGINT,
    last_event_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_cursors_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_device_cursors_updated ON device_cursors(updated_at);

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS attendance_sync_interval_minutes INTEGER NOT NULL DEFAULT 2;

ALTER TABLE tenants
    ADD CONSTRAINT chk_tenants_attendance_sync_interval
    CHECK (attendance_sync_interval_minutes IN (2, 5, 10, 15, 30, 60));

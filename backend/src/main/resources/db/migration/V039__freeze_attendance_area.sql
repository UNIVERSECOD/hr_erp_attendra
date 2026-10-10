-- Freeze the best currently known area for existing sessions. Earlier moves cannot
-- be reconstructed if no history was recorded. New sessions capture at insertion.
ALTER TABLE attendance_logs ADD COLUMN area_snapshot_id BIGINT;
ALTER TABLE attendance_logs ADD COLUMN area_snapshot_name VARCHAR(255);
ALTER TABLE attendance_logs ADD COLUMN area_snapshot_captured BOOLEAN NOT NULL DEFAULT FALSE;

CREATE FUNCTION capture_attendance_area() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE area_id BIGINT;
BEGIN
    IF NEW.area_snapshot_captured THEN RETURN NEW; END IF;
    -- ISAPI bridge identifier takes precedence over legacy backend identifiers.
    SELECT d.branch_id INTO area_id FROM device_configs d
    WHERE d.tenant_id = NEW.tenant_id
      AND (d.device_id = NEW.device_id OR
           (NEW.event_type IS DISTINCT FROM 'DOOR_SESSION' AND d.id::text = NEW.device_id))
    ORDER BY (d.device_id = NEW.device_id) DESC, d.id LIMIT 1;
    IF area_id IS NULL THEN
        SELECT e.branch_id INTO area_id FROM employees e
        WHERE e.id = NEW.employee_id AND e.tenant_id = NEW.tenant_id;
    END IF;
    SELECT b.id, b.name INTO NEW.area_snapshot_id, NEW.area_snapshot_name
    FROM branches b WHERE b.id = area_id AND b.tenant_id = NEW.tenant_id;
    NEW.area_snapshot_captured := TRUE;
    RETURN NEW;
END $$;

CREATE TRIGGER attendance_area_snapshot BEFORE INSERT OR UPDATE ON attendance_logs
FOR EACH ROW EXECUTE FUNCTION capture_attendance_area();

UPDATE attendance_logs SET area_snapshot_captured = FALSE
WHERE NOT area_snapshot_captured;

CREATE INDEX idx_audit_logs_tenant_time ON audit_logs(tenant_id, created_at DESC, id DESC);

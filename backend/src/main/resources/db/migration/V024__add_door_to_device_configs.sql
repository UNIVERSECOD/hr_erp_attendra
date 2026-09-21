ALTER TABLE device_configs
    ADD COLUMN IF NOT EXISTS door_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS door_role VARCHAR(10) NULL;

ALTER TABLE device_configs
    ADD CONSTRAINT fk_device_configs_door
    FOREIGN KEY (door_id) REFERENCES doors(id);

CREATE UNIQUE INDEX idx_device_door_role ON device_configs(door_id, door_role) WHERE door_id IS NOT NULL;

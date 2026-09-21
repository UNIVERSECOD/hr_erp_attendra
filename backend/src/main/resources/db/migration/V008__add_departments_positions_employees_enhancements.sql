-- Add description and parent_department_id to departments
ALTER TABLE departments ADD COLUMN IF NOT EXISTS description VARCHAR(500);
ALTER TABLE departments ADD COLUMN IF NOT EXISTS parent_department_id BIGINT REFERENCES departments(id);

-- Add description to positions
ALTER TABLE positions ADD COLUMN IF NOT EXISTS description VARCHAR(500);

-- Add father_name and area to employees
ALTER TABLE employees ADD COLUMN IF NOT EXISTS father_name VARCHAR(100);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS area VARCHAR(100);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS shift_type VARCHAR(50);

-- Seed data removed: departments, positions, and employees are now imported from the Hikvision device.

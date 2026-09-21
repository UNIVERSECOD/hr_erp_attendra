CREATE TABLE IF NOT EXISTS branches (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50),
    city VARCHAR(100),
    address TEXT,
    is_head_office BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'branches' AND column_name = 'branch_name'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'branches' AND column_name = 'name'
    ) THEN
        ALTER TABLE branches RENAME COLUMN branch_name TO name;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'branches' AND column_name = 'branch_code'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'branches' AND column_name = 'code'
    ) THEN
        ALTER TABLE branches RENAME COLUMN branch_code TO code;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'branches' AND column_name = 'location'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'branches' AND column_name = 'city'
    ) THEN
        ALTER TABLE branches RENAME COLUMN location TO city;
    END IF;
END $$;

ALTER TABLE branches ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE branches ADD COLUMN IF NOT EXISTS name VARCHAR(255);
ALTER TABLE branches ADD COLUMN IF NOT EXISTS code VARCHAR(50);
ALTER TABLE branches ADD COLUMN IF NOT EXISTS city VARCHAR(100);
ALTER TABLE branches ADD COLUMN IF NOT EXISTS address TEXT;
ALTER TABLE branches ADD COLUMN IF NOT EXISTS is_head_office BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE branches ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE branches ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE branches ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE branches SET tenant_id = COALESCE(tenant_id, 1);
UPDATE branches SET status = 'ACTIVE' WHERE status IS NULL;
UPDATE branches SET name = COALESCE(name, code, 'Branch-' || id) WHERE name IS NULL;

ALTER TABLE branches ALTER COLUMN tenant_id SET DEFAULT 1;
ALTER TABLE branches ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE branches ALTER COLUMN name TYPE VARCHAR(255);
ALTER TABLE branches ALTER COLUMN name SET NOT NULL;
ALTER TABLE branches ALTER COLUMN code DROP NOT NULL;
ALTER TABLE branches ALTER COLUMN is_head_office SET DEFAULT FALSE;
ALTER TABLE branches ALTER COLUMN is_head_office SET NOT NULL;
ALTER TABLE branches ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE branches ALTER COLUMN status SET NOT NULL;
ALTER TABLE branches ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE branches ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE branches ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE branches ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE employees ADD COLUMN IF NOT EXISTS branch_id BIGINT;
ALTER TABLE departments ADD COLUMN IF NOT EXISTS branch_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_branches_tenant_id ON branches(tenant_id);
CREATE INDEX IF NOT EXISTS idx_employees_tenant_branch_id ON employees(tenant_id, branch_id);
CREATE INDEX IF NOT EXISTS idx_departments_tenant_branch_id ON departments(tenant_id, branch_id);

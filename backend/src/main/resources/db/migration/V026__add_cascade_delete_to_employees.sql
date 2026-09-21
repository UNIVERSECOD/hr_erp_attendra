-- Drop old constraints and recreate them with ON DELETE CASCADE to allow smooth employee deletion

-- 1. work_schedules
ALTER TABLE work_schedules
    DROP CONSTRAINT IF EXISTS work_schedules_employee_id_fkey,
    ADD CONSTRAINT work_schedules_employee_id_fkey
        FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE;

-- 2. attendance_logs
ALTER TABLE attendance_logs
    DROP CONSTRAINT IF EXISTS attendance_logs_employee_id_fkey,
    ADD CONSTRAINT attendance_logs_employee_id_fkey
        FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE;

-- 3. daily_attendance_summaries
ALTER TABLE daily_attendance_summaries
    DROP CONSTRAINT IF EXISTS daily_attendance_summaries_employee_id_fkey,
    ADD CONSTRAINT daily_attendance_summaries_employee_id_fkey
        FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE;

-- 4. leave_requests
ALTER TABLE leave_requests
    DROP CONSTRAINT IF EXISTS leave_requests_employee_id_fkey,
    ADD CONSTRAINT leave_requests_employee_id_fkey
        FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE;

-- 5. face_data
ALTER TABLE face_data
    DROP CONSTRAINT IF EXISTS face_data_employee_id_fkey,
    ADD CONSTRAINT face_data_employee_id_fkey
        FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE;

-- 6. access_cards
ALTER TABLE access_cards
    DROP CONSTRAINT IF EXISTS access_cards_employee_id_fkey,
    ADD CONSTRAINT access_cards_employee_id_fkey
        FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE;

-- Clean up and add foreign keys for tables that didn't have constraints originally:

-- 7. attendance_records
DELETE FROM attendance_records WHERE employee_id NOT IN (SELECT id FROM employees);
ALTER TABLE attendance_records
    DROP CONSTRAINT IF EXISTS fk_attendance_records_employee,
    ADD CONSTRAINT fk_attendance_records_employee
        FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE;

-- 8. annual_leave_balances
DELETE FROM annual_leave_balances WHERE employee_id NOT IN (SELECT id FROM employees);
ALTER TABLE annual_leave_balances
    DROP CONSTRAINT IF EXISTS fk_annual_leave_balances_employee,
    ADD CONSTRAINT fk_annual_leave_balances_employee
        FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE;

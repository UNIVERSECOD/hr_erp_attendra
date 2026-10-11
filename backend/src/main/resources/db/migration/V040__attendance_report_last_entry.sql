-- Additive: preserve original entries, exits, manual corrections and daily totals.
-- Null on existing rows means the original check-in is the latest known entry.
ALTER TABLE attendance_logs ADD COLUMN last_entry_time TIMESTAMP;

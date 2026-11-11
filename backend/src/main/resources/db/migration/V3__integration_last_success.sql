-- Timestamp of the last health probe that succeeded (distinct from the last probe attempt).
ALTER TABLE controller_connections ADD COLUMN last_success_at DATETIME(3) NULL;

-- V3: Payment Method Code System Support
-- Note: This migration was originally intended to add the 'code' column.
-- Since the column is now handled in V1 for fresh installs and via self-healing logic 
-- in DatabaseManager for existing ones, this file is kept for version consistency.
-- The actual column addition is now idempotent.
SELECT 1;

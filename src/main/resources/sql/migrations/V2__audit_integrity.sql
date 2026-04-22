-- Migration: V2__audit_integrity.sql
-- Description: Adds SHA-256 integrity hash column to audit logs for tamper-evidence.

ALTER TABLE audit_log ADD COLUMN integrity_hash TEXT;

-- Create an index for the timeline to ensure fast lookups during verification
DROP INDEX IF EXISTS idx_audit_timeline;
CREATE INDEX idx_audit_timeline ON audit_log(created_at, id);

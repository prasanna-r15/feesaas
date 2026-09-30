-- Runs once on a fresh database, as the owner/superuser.
-- The application connects as feesaas_app: NOT an owner, NOT superuser, NOT BYPASSRLS,
-- so PostgreSQL Row-Level Security always applies to it.
CREATE ROLE feesaas_app LOGIN PASSWORD 'feesaas_app' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;

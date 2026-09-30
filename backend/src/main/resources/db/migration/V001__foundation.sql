-- =====================================================================
-- V001  Foundation: tenancy helpers, tenants, users, auth, audit
-- Runs as the OWNER role. The runtime role (feesaas_app) is created by
-- infra/db/init.sql and must exist before this migration.
-- =====================================================================

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'feesaas_app') THEN
    RAISE EXCEPTION 'Role feesaas_app is missing. Run infra/db/init.sql first.';
  END IF;
END $$;

CREATE EXTENSION IF NOT EXISTS pg_trgm;

GRANT USAGE ON SCHEMA public TO feesaas_app;
-- every table created from now on by the owner is usable by the app role
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO feesaas_app;

-- ---------------------------------------------------------------------
-- Request scope helpers. The application sets these per TRANSACTION with
-- set_config(..., is_local => true). Missing/empty => NULL / off (fail closed).
-- ---------------------------------------------------------------------
CREATE FUNCTION app_tenant_id() RETURNS uuid
  LANGUAGE sql STABLE
  AS $$ SELECT nullif(current_setting('app.tenant_id', true), '')::uuid $$;

CREATE FUNCTION app_is_platform() RETURNS boolean
  LANGUAGE sql STABLE
  AS $$ SELECT coalesce(nullif(current_setting('app.is_platform', true), ''), 'off') = 'on' $$;

-- ---------------------------------------------------------------------
-- Reference data (global, not tenant-owned)
-- ---------------------------------------------------------------------
CREATE TABLE permissions (
  code        text PRIMARY KEY,
  module      text NOT NULL,
  description text NOT NULL
);

CREATE TABLE roles (
  code        text PRIMARY KEY,
  name        text NOT NULL,
  description text
);

CREATE TABLE role_permissions (
  role_code       text NOT NULL REFERENCES roles (code),
  permission_code text NOT NULL REFERENCES permissions (code),
  PRIMARY KEY (role_code, permission_code)
);

-- ---------------------------------------------------------------------
-- Tenants
-- ---------------------------------------------------------------------
CREATE TABLE tenants (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name          text        NOT NULL,
  slug          text        NOT NULL UNIQUE,
  business_type text        NOT NULL,
  status        text        NOT NULL DEFAULT 'ONBOARDING'
                CHECK (status IN ('ONBOARDING','ACTIVE','SUSPENDED','CANCELLED')),
  timezone      text        NOT NULL DEFAULT 'Asia/Kolkata',
  currency      char(3)     NOT NULL DEFAULT 'INR',
  db_shard      text        NOT NULL DEFAULT 'default',   -- reserved for moving big tenants
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  version       bigint      NOT NULL DEFAULT 0
);

CREATE TABLE tenant_settings (
  tenant_id               uuid PRIMARY KEY REFERENCES tenants (id) ON DELETE CASCADE,
  phone                   text,
  whatsapp_number         text,
  email                   text,
  website                 text,
  address_line            text,
  city                    text,
  state                   text,
  postal_code             text,
  country                 char(2) NOT NULL DEFAULT 'IN',
  socials                 jsonb   NOT NULL DEFAULT '{}'::jsonb,
  working_hours           jsonb   NOT NULL DEFAULT '{}'::jsonb,
  labels                  jsonb   NOT NULL DEFAULT '{}'::jsonb,   -- terminology: customer.singular, ...
  onboarding_step         int     NOT NULL DEFAULT 1,
  onboarding_completed_at timestamptz,
  updated_at              timestamptz NOT NULL DEFAULT now(),
  version                 bigint  NOT NULL DEFAULT 0
);

CREATE TABLE tenant_modules (
  tenant_id   uuid    NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
  module_code text    NOT NULL,
  enabled     boolean NOT NULL DEFAULT true,
  config      jsonb   NOT NULL DEFAULT '{}'::jsonb,
  updated_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, module_code)
);

-- Per-tenant gap-free sequences (customer codes, receipt numbers).
CREATE TABLE tenant_counters (
  tenant_id   uuid   NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
  counter_key text   NOT NULL,
  value       bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (tenant_id, counter_key)
);

CREATE FUNCTION next_counter(p_key text) RETURNS bigint
  LANGUAGE sql
  AS $$
    INSERT INTO tenant_counters (tenant_id, counter_key, value)
    VALUES (app_tenant_id(), p_key, 1)
    ON CONFLICT (tenant_id, counter_key)
    DO UPDATE SET value = tenant_counters.value + 1
    RETURNING value
  $$;

-- ---------------------------------------------------------------------
-- Users. tenant_id is NULL only for platform super admins.
-- ---------------------------------------------------------------------
CREATE TABLE users (
  id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id          uuid REFERENCES tenants (id),
  email              text,
  phone              text,
  full_name          text        NOT NULL,
  password_hash      text        NOT NULL,
  role_code          text        NOT NULL REFERENCES roles (code),
  status             text        NOT NULL DEFAULT 'ACTIVE'
                     CHECK (status IN ('ACTIVE','INVITED','DISABLED')),
  token_version      int         NOT NULL DEFAULT 0,   -- bump to invalidate all access tokens
  failed_login_count int         NOT NULL DEFAULT 0,
  locked_until       timestamptz,
  last_login_at      timestamptz,
  totp_secret        text,
  created_at         timestamptz NOT NULL DEFAULT now(),
  updated_at         timestamptz NOT NULL DEFAULT now(),
  version            bigint      NOT NULL DEFAULT 0,
  CONSTRAINT uq_users_tenant_id UNIQUE (tenant_id, id),
  CONSTRAINT ck_users_identifier CHECK (email IS NOT NULL OR phone IS NOT NULL),
  CONSTRAINT ck_users_platform_role CHECK ((role_code = 'PLATFORM_SUPER_ADMIN') = (tenant_id IS NULL))
);
CREATE UNIQUE INDEX uq_users_email ON users (lower(email)) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX uq_users_phone ON users (phone)        WHERE phone IS NOT NULL;
CREATE INDEX ix_users_tenant ON users (tenant_id);

-- Explicit per-user permission grants (used for STAFF; owners get the role defaults).
CREATE TABLE user_permissions (
  tenant_id       uuid NOT NULL,
  user_id         uuid NOT NULL,
  permission_code text NOT NULL REFERENCES permissions (code),
  PRIMARY KEY (user_id, permission_code),
  FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id) ON DELETE CASCADE
);

-- Refresh tokens: opaque, stored HASHED, rotated on use, grouped in families
-- so reuse of an old token revokes the whole family.
-- Not RLS-protected: looked up by a 256-bit secret hash before tenant scope is known.
CREATE TABLE refresh_tokens (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  tenant_id   uuid,
  family_id   uuid        NOT NULL,
  token_hash  text        NOT NULL UNIQUE,
  device_id   text,
  issued_at   timestamptz NOT NULL DEFAULT now(),
  expires_at  timestamptz NOT NULL,
  used_at     timestamptz,
  revoked_at  timestamptz,
  replaced_by uuid
);
CREATE INDEX ix_refresh_tokens_user   ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);

-- ---------------------------------------------------------------------
-- Audit log: append-only, partitioned by month (default partition catches all
-- until the monthly partition job is added in Phase 11).
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs (
  id          uuid        NOT NULL DEFAULT gen_random_uuid(),
  tenant_id   uuid,
  user_id     uuid,
  action      text        NOT NULL,
  entity_type text        NOT NULL,
  entity_id   text,
  old_value   jsonb,
  new_value   jsonb,
  ip_address  text,
  request_id  text,
  created_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE audit_logs_default PARTITION OF audit_logs DEFAULT;
CREATE INDEX ix_audit_entity ON audit_logs (tenant_id, entity_type, entity_id, created_at DESC);
CREATE INDEX ix_audit_user   ON audit_logs (tenant_id, user_id, created_at DESC);

REVOKE UPDATE, DELETE ON audit_logs, audit_logs_default FROM feesaas_app;

-- ---------------------------------------------------------------------
-- Row-Level Security. Fail closed: no scope => zero rows, writes rejected.
-- (Table owner bypasses RLS by design; the app never connects as owner.)
-- ---------------------------------------------------------------------
ALTER TABLE tenants           ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_settings   ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_modules    ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_counters   ENABLE ROW LEVEL SECURITY;
ALTER TABLE users             ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_permissions  ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs        ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs_default ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON tenants
  USING (id = app_tenant_id() OR app_is_platform());
CREATE POLICY tenant_isolation ON tenant_settings
  USING (tenant_id = app_tenant_id() OR app_is_platform());
CREATE POLICY tenant_isolation ON tenant_modules
  USING (tenant_id = app_tenant_id() OR app_is_platform());
CREATE POLICY tenant_isolation ON tenant_counters
  USING (tenant_id = app_tenant_id());
CREATE POLICY tenant_isolation ON users
  USING (tenant_id = app_tenant_id() OR app_is_platform());
CREATE POLICY tenant_isolation ON user_permissions
  USING (tenant_id = app_tenant_id() OR app_is_platform());
CREATE POLICY tenant_isolation ON audit_logs
  USING (tenant_id = app_tenant_id() OR app_is_platform());
CREATE POLICY tenant_isolation ON audit_logs_default
  USING (tenant_id = app_tenant_id() OR app_is_platform());

-- ---------------------------------------------------------------------
-- Login lookup: at login the tenant is not yet known, and RLS would hide the
-- user row. This narrow SECURITY DEFINER function is the ONLY door around RLS,
-- and it returns just the columns authentication needs.
-- ---------------------------------------------------------------------
CREATE FUNCTION auth_find_user(p_identifier text)
RETURNS TABLE (id uuid, tenant_id uuid, full_name text, password_hash text, role_code text,
               status text, token_version int, failed_login_count int, locked_until timestamptz)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
  SELECT u.id, u.tenant_id, u.full_name, u.password_hash, u.role_code,
         u.status, u.token_version, u.failed_login_count, u.locked_until
  FROM users u
  WHERE lower(u.email) = lower(p_identifier) OR u.phone = p_identifier
  LIMIT 1
$$;
REVOKE ALL ON FUNCTION auth_find_user(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth_find_user(text) TO feesaas_app;

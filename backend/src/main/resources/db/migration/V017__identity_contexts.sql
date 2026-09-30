-- Personal identity + memberships. Does not alter fee/customer tables.
-- Widens users CHECK so INDIVIDUAL accounts may have tenant_id NULL.

ALTER TABLE users DROP CONSTRAINT ck_users_platform_role;
ALTER TABLE users ADD CONSTRAINT ck_users_platform_role CHECK (
  (role_code = 'PLATFORM_SUPER_ADMIN' AND tenant_id IS NULL)
  OR (role_code = 'INDIVIDUAL' AND tenant_id IS NULL)
  OR (role_code NOT IN ('PLATFORM_SUPER_ADMIN', 'INDIVIDUAL') AND tenant_id IS NOT NULL)
);

INSERT INTO roles (code, name, description) VALUES
  ('INDIVIDUAL', 'Individual', 'Personal finance and group split user')
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code, module, description) VALUES
  ('personal.manage', 'PERSONAL', 'Manage personal workspace, expenses, income, and budgets'),
  ('groups.manage', 'GROUPS', 'Create and manage expense groups')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code) VALUES
  ('INDIVIDUAL', 'personal.manage'),
  ('INDIVIDUAL', 'groups.manage')
ON CONFLICT DO NOTHING;

CREATE FUNCTION app_user_id() RETURNS uuid
  LANGUAGE sql STABLE
  AS $$ SELECT nullif(current_setting('app.user_id', true), '')::uuid $$;

DROP POLICY IF EXISTS tenant_isolation ON users;
CREATE POLICY tenant_isolation ON users
  USING (tenant_id = app_tenant_id() OR app_is_platform() OR id = app_user_id());

CREATE TABLE user_profiles (
  user_id              uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
  default_kind         text,
  default_tenant_id    uuid REFERENCES tenants (id),
  default_workspace_id uuid,
  default_group_id     uuid,
  onboarding_done      boolean NOT NULL DEFAULT false,
  updated_at           timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE personal_workspaces (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_user_id uuid NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
  name          text NOT NULL,
  currency      char(3) NOT NULL DEFAULT 'INR',
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE user_profiles
  ADD CONSTRAINT fk_profile_workspace FOREIGN KEY (default_workspace_id) REFERENCES personal_workspaces (id);

CREATE TABLE user_memberships (
  id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id            uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  kind               text NOT NULL CHECK (kind IN ('PERSONAL', 'BUSINESS', 'GROUP')),
  role_code          text NOT NULL,
  business_tenant_id uuid REFERENCES tenants (id) ON DELETE CASCADE,
  workspace_id       uuid REFERENCES personal_workspaces (id) ON DELETE CASCADE,
  group_id           uuid,
  created_at         timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_membership_target CHECK (
    (kind = 'PERSONAL' AND workspace_id IS NOT NULL AND business_tenant_id IS NULL AND group_id IS NULL)
    OR (kind = 'BUSINESS' AND business_tenant_id IS NOT NULL AND workspace_id IS NULL AND group_id IS NULL)
    OR (kind = 'GROUP' AND group_id IS NOT NULL AND workspace_id IS NULL AND business_tenant_id IS NULL)
  )
);
CREATE UNIQUE INDEX uq_membership_personal ON user_memberships (user_id) WHERE kind = 'PERSONAL';
CREATE UNIQUE INDEX uq_membership_business ON user_memberships (user_id, business_tenant_id) WHERE kind = 'BUSINESS';
CREATE INDEX ix_membership_user ON user_memberships (user_id);

ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS context_kind text;
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS workspace_id uuid;
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS group_id uuid;

CREATE OR REPLACE FUNCTION auth_session_state(p_user_id uuid)
RETURNS TABLE (
    token_version int,
    user_status   text,
    tenant_status text,
    tenant_id     uuid,
    role_code     text,
    full_name     text
)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
  SELECT u.token_version, u.status, t.status, u.tenant_id, u.role_code, u.full_name
  FROM users u
  LEFT JOIN tenants t ON t.id = u.tenant_id
  WHERE u.id = p_user_id
$$;

CREATE OR REPLACE FUNCTION auth_register_individual(
  p_full_name text,
  p_email text,
  p_phone text,
  p_password_hash text
) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  v_user uuid;
  v_ws uuid;
BEGIN
  IF p_email IS NULL AND p_phone IS NULL THEN
    RAISE EXCEPTION 'identifier required';
  END IF;
  INSERT INTO users (id, tenant_id, email, phone, full_name, password_hash, role_code, status)
  VALUES (gen_random_uuid(), NULL, p_email, p_phone, p_full_name, p_password_hash, 'INDIVIDUAL', 'ACTIVE')
  RETURNING id INTO v_user;

  INSERT INTO personal_workspaces (id, owner_user_id, name)
  VALUES (gen_random_uuid(), v_user, p_full_name || '''s money')
  RETURNING id INTO v_ws;

  INSERT INTO user_profiles (user_id, default_kind, default_workspace_id, onboarding_done)
  VALUES (v_user, 'PERSONAL', v_ws, false);

  INSERT INTO user_memberships (id, user_id, kind, role_code, workspace_id)
  VALUES (gen_random_uuid(), v_user, 'PERSONAL', 'INDIVIDUAL', v_ws);

  RETURN v_user;
END;
$$;
REVOKE ALL ON FUNCTION auth_register_individual(text, text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth_register_individual(text, text, text, text) TO feesaas_app;

CREATE FUNCTION auth_workspace_of(p_user_id uuid) RETURNS uuid
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$ SELECT id FROM personal_workspaces WHERE owner_user_id = p_user_id $$;
REVOKE ALL ON FUNCTION auth_workspace_of(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth_workspace_of(uuid) TO feesaas_app;

ALTER TABLE personal_workspaces ENABLE ROW LEVEL SECURITY;
CREATE POLICY owner_only ON personal_workspaces
  USING (owner_user_id = app_user_id());

ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;
CREATE POLICY owner_only ON user_profiles
  USING (user_id = app_user_id());

ALTER TABLE user_memberships ENABLE ROW LEVEL SECURITY;
CREATE POLICY owner_only ON user_memberships
  USING (user_id = app_user_id());

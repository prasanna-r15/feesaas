-- Session lookup used on every authenticated request (token_version + tenant status).
-- SECURITY DEFINER so the app role can check without an RLS scope already applied.
CREATE FUNCTION auth_session_state(p_user_id uuid)
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
REVOKE ALL ON FUNCTION auth_session_state(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth_session_state(uuid) TO feesaas_app;

-- Looked up by token hash before tenant scope is known (same pattern as refresh_tokens).
CREATE TABLE password_reset_tokens (
  id          uuid PRIMARY KEY,
  user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  token_hash  text        NOT NULL UNIQUE,
  expires_at  timestamptz NOT NULL,
  used_at     timestamptz,
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_password_reset_user ON password_reset_tokens (user_id);

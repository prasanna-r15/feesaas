INSERT INTO role_permissions (role_code, permission_code) VALUES
  ('BUSINESS_OWNER', 'personal.manage'),
  ('BUSINESS_OWNER', 'groups.manage'),
  ('STAFF', 'personal.manage'),
  ('STAFF', 'groups.manage')
ON CONFLICT DO NOTHING;

CREATE OR REPLACE FUNCTION auth_ensure_personal_workspace(p_user_id uuid) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  v_ws uuid;
  v_role text;
  v_name text;
BEGIN
  SELECT role_code, full_name INTO v_role, v_name FROM users WHERE id = p_user_id;
  IF v_role IS NULL OR v_role = 'PLATFORM_SUPER_ADMIN' THEN
    RETURN NULL;
  END IF;

  SELECT id INTO v_ws FROM personal_workspaces WHERE owner_user_id = p_user_id;
  IF v_ws IS NULL THEN
    INSERT INTO personal_workspaces (id, owner_user_id, name)
    VALUES (gen_random_uuid(), p_user_id, coalesce(v_name, 'My') || '''s money')
    RETURNING id INTO v_ws;
  END IF;

  INSERT INTO user_memberships (id, user_id, kind, role_code, workspace_id)
  SELECT gen_random_uuid(), p_user_id, 'PERSONAL', 'INDIVIDUAL', v_ws
  WHERE NOT EXISTS (
    SELECT 1 FROM user_memberships m WHERE m.user_id = p_user_id AND m.kind = 'PERSONAL'
  );

  INSERT INTO user_profiles (user_id, default_kind, default_workspace_id, onboarding_done)
  VALUES (
    p_user_id,
    CASE WHEN v_role = 'INDIVIDUAL' THEN 'PERSONAL' ELSE 'BUSINESS' END,
    v_ws,
    v_role <> 'INDIVIDUAL'
  )
  ON CONFLICT (user_id) DO UPDATE SET
    default_workspace_id = coalesce(user_profiles.default_workspace_id, excluded.default_workspace_id),
    updated_at = now();

  PERFORM seed_personal_categories(v_ws);
  RETURN v_ws;
END;
$$;
REVOKE ALL ON FUNCTION auth_ensure_personal_workspace(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth_ensure_personal_workspace(uuid) TO feesaas_app;

CREATE OR REPLACE FUNCTION auth_attach_business_membership(p_user_id uuid, p_tenant uuid, p_role text)
RETURNS int
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
BEGIN
  IF p_user_id IS NULL OR p_tenant IS NULL THEN
    RETURN 0;
  END IF;
  INSERT INTO user_memberships (id, user_id, kind, role_code, business_tenant_id)
  SELECT gen_random_uuid(), p_user_id, 'BUSINESS', coalesce(p_role, 'STAFF'), p_tenant
   WHERE NOT EXISTS (
     SELECT 1 FROM user_memberships m
      WHERE m.user_id = p_user_id AND m.kind = 'BUSINESS' AND m.business_tenant_id = p_tenant
   );
  RETURN 1;
END;
$$;
REVOKE ALL ON FUNCTION auth_attach_business_membership(uuid, uuid, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth_attach_business_membership(uuid, uuid, text) TO feesaas_app;

CREATE TABLE business_join_enquiries (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id        uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  full_name      text NOT NULL,
  email          text,
  phone          text,
  business_name  text NOT NULL,
  city           text,
  message        text,
  status         text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'EMAILED', 'CLOSED')),
  created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_join_enquiries_created ON business_join_enquiries (created_at DESC);

ALTER TABLE business_join_enquiries ENABLE ROW LEVEL SECURITY;
CREATE POLICY own_or_platform ON business_join_enquiries
  USING (user_id = app_user_id() OR app_is_platform())
  WITH CHECK (user_id = app_user_id() OR app_is_platform());

INSERT INTO platform_config (param_key, param_sub_key, param_value, description, locked) VALUES
(
  'JOIN_BUSINESS_SUBJECT',
  'DEFAULT',
  'Join-business enquiry · {{businessName}}',
  'Subject for gym join enquiries. Placeholders: {{name}} {{email}} {{phone}} {{businessName}} {{city}} {{message}}',
  true
),
(
  'JOIN_BUSINESS_BODY',
  'DEFAULT',
  '<p>A DueMate individual asked to join a business.</p><p><b>Name:</b> {{name}}<br/><b>Email:</b> {{email}}<br/><b>Phone:</b> {{phone}}<br/><b>Business:</b> {{businessName}}<br/><b>City:</b> {{city}}</p><p>{{message}}</p>',
  'HTML body for gym join enquiries.',
  true
)
ON CONFLICT (param_key, param_sub_key) DO NOTHING;

INSERT INTO user_memberships (id, user_id, kind, role_code, business_tenant_id)
SELECT gen_random_uuid(), u.id, 'BUSINESS', u.role_code, u.tenant_id
  FROM users u
 WHERE u.tenant_id IS NOT NULL
   AND u.role_code IN ('BUSINESS_OWNER', 'STAFF')
   AND NOT EXISTS (
     SELECT 1 FROM user_memberships m
      WHERE m.user_id = u.id AND m.kind = 'BUSINESS' AND m.business_tenant_id = u.tenant_id
   );

SELECT auth_ensure_personal_workspace(u.id)
  FROM users u
 WHERE u.role_code IN ('BUSINESS_OWNER', 'STAFF', 'INDIVIDUAL');

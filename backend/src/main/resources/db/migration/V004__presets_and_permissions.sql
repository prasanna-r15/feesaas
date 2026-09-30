-- Readable catalogue of business-type defaults (copied into the tenant at creation).
CREATE TABLE business_type_presets (
  code        text PRIMARY KEY,
  name        text NOT NULL,
  labels      jsonb NOT NULL DEFAULT '{}'::jsonb,
  modules     jsonb NOT NULL DEFAULT '[]'::jsonb
);

INSERT INTO business_type_presets (code, name, labels, modules) VALUES
  ('GYM', 'Gym / Fitness',
   '{"customer.singular":"Member","customer.plural":"Members","batch.singular":"Batch","batch.plural":"Batches"}'::jsonb,
   '["CUSTOMERS","FEES","PAYMENTS","MEMBERSHIP","NOTIFICATIONS","REPORTS","SETTINGS"]'::jsonb),
  ('ACADEMY', 'Academy',
   '{"customer.singular":"Student","customer.plural":"Students","batch.singular":"Batch","batch.plural":"Batches"}'::jsonb,
   '["CUSTOMERS","FEES","PAYMENTS","ATTENDANCE","NOTIFICATIONS","REPORTS","SETTINGS"]'::jsonb),
  ('TUITION', 'Tuition centre',
   '{"customer.singular":"Student","customer.plural":"Students","batch.singular":"Batch","batch.plural":"Batches"}'::jsonb,
   '["CUSTOMERS","FEES","PAYMENTS","ATTENDANCE","NOTIFICATIONS","REPORTS","SETTINGS"]'::jsonb),
  ('GENERIC', 'General',
   '{"customer.singular":"Customer","customer.plural":"Customers","batch.singular":"Batch","batch.plural":"Batches"}'::jsonb,
   '["CUSTOMERS","FEES","PAYMENTS","NOTIFICATIONS","REPORTS","SETTINGS"]'::jsonb);

REVOKE INSERT, UPDATE, DELETE ON business_type_presets FROM feesaas_app;

-- Permission check without an RLS transaction (used by method security).
CREATE FUNCTION auth_permissions(p_user_id uuid)
RETURNS TABLE (permission_code text)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
  SELECT rp.permission_code
    FROM users u
    JOIN role_permissions rp ON rp.role_code = u.role_code
   WHERE u.id = p_user_id
     AND u.role_code <> 'STAFF'
  UNION
  SELECT up.permission_code
    FROM user_permissions up
   WHERE up.user_id = p_user_id
     AND EXISTS (SELECT 1 FROM users u WHERE u.id = p_user_id AND u.role_code = 'STAFF')
$$;
REVOKE ALL ON FUNCTION auth_permissions(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth_permissions(uuid) TO feesaas_app;

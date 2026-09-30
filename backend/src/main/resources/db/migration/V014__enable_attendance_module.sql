INSERT INTO tenant_modules (tenant_id, module_code, enabled)
SELECT t.id, 'ATTENDANCE', true
  FROM tenants t
 WHERE t.business_type IN ('GYM', 'ACADEMY')
ON CONFLICT (tenant_id, module_code) DO UPDATE SET enabled = true;

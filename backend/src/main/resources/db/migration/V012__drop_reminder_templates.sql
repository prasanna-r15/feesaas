DROP TABLE IF EXISTS tenant_media;
DROP TABLE IF EXISTS reminder_templates;

INSERT INTO role_permissions (role_code, permission_code)
VALUES ('STAFF', 'reports.view')
ON CONFLICT DO NOTHING;

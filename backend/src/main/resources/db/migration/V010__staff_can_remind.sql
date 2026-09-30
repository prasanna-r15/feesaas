INSERT INTO role_permissions (role_code, permission_code)
VALUES ('STAFF', 'reminders.send')
ON CONFLICT DO NOTHING;

-- Permission catalogue. Code-defined; tenants pick from it for staff.
INSERT INTO permissions (code, module, description) VALUES
  ('customers.view',     'CUSTOMERS',     'View customers'),
  ('customers.create',   'CUSTOMERS',     'Add customers'),
  ('customers.edit',     'CUSTOMERS',     'Edit customers'),
  ('customers.delete',   'CUSTOMERS',     'Delete customers'),
  ('imports.run',        'CUSTOMERS',     'Import customers from CSV/Excel'),
  ('fees.view',          'FEES',          'View fees and pending dues'),
  ('fees.manage',        'FEES',          'Manage fee plans, adjustments, waivers'),
  ('payments.view',      'PAYMENTS',      'View payments'),
  ('payments.record',    'PAYMENTS',      'Record payments'),
  ('payments.void',      'PAYMENTS',      'Void payments'),
  ('reminders.send',     'NOTIFICATIONS', 'Send reminders and announcements'),
  ('templates.manage',   'NOTIFICATIONS', 'Manage message templates and reminder rules'),
  ('memberships.manage', 'MEMBERSHIP',    'Manage memberships'),
  ('attendance.manage',  'ATTENDANCE',    'Mark and edit attendance'),
  ('batches.manage',     'ATTENDANCE',    'Manage batches and classes'),
  ('reports.view',       'REPORTS',       'View and export reports'),
  ('staff.manage',       'STAFF',         'Manage staff and their permissions'),
  ('settings.manage',    'SETTINGS',      'Manage business profile and configuration'),
  ('audit.view',         'SETTINGS',      'View audit log'),
  ('platform.tenants.manage', 'PLATFORM', 'Create and manage tenants'),
  ('platform.plans.manage',   'PLATFORM', 'Manage subscription plans'),
  ('platform.flags.manage',   'PLATFORM', 'Manage feature flags'),
  ('platform.stats.view',     'PLATFORM', 'View platform statistics');

INSERT INTO roles (code, name, description) VALUES
  ('PLATFORM_SUPER_ADMIN', 'Platform Super Admin', 'SaaS operator'),
  ('BUSINESS_OWNER',       'Business Owner',       'Full access within own tenant'),
  ('STAFF',                'Staff',                'Per-user permissions within own tenant');

INSERT INTO role_permissions (role_code, permission_code)
  SELECT 'PLATFORM_SUPER_ADMIN', code FROM permissions WHERE module = 'PLATFORM';
INSERT INTO role_permissions (role_code, permission_code)
  SELECT 'BUSINESS_OWNER', code FROM permissions WHERE module <> 'PLATFORM';
INSERT INTO role_permissions (role_code, permission_code) VALUES
  ('STAFF', 'customers.view'), ('STAFF', 'fees.view'), ('STAFF', 'payments.view');

-- Reference data is read-only for the application role.
REVOKE INSERT, UPDATE, DELETE ON permissions, roles, role_permissions FROM feesaas_app;

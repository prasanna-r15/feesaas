CREATE TABLE platform_plans (
  code         text PRIMARY KEY,
  name         text NOT NULL,
  max_members  int  NOT NULL CHECK (max_members >= 0),
  max_staff    int  NOT NULL CHECK (max_staff >= 0),
  price_minor  bigint NOT NULL DEFAULT 0
);

INSERT INTO platform_plans (code, name, max_members, max_staff, price_minor) VALUES
  ('FREE', 'Free', 50, 2, 0),
  ('PRO',  'Pro',  2000, 20, 0);

ALTER TABLE tenants
  ADD COLUMN plan_code text NOT NULL DEFAULT 'FREE' REFERENCES platform_plans (code);

UPDATE tenants SET plan_code = 'PRO';

ALTER TABLE payments
  ADD COLUMN void_reason text,
  ADD COLUMN voided_at   timestamptz,
  ADD COLUMN voided_by   uuid REFERENCES users (id) ON DELETE SET NULL;

CREATE TABLE batches (
  id          uuid PRIMARY KEY,
  tenant_id   uuid NOT NULL REFERENCES tenants (id),
  name        text NOT NULL,
  schedule    text,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now(),
  deleted_at  timestamptz,
  version     bigint NOT NULL DEFAULT 0,
  CONSTRAINT uq_batches_tenant_id UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX uq_batches_name ON batches (tenant_id, lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE batch_members (
  tenant_id    uuid NOT NULL REFERENCES tenants (id),
  batch_id     uuid NOT NULL,
  customer_id  uuid NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (batch_id, customer_id),
  CONSTRAINT fk_bm_batch FOREIGN KEY (tenant_id, batch_id) REFERENCES batches (tenant_id, id),
  CONSTRAINT fk_bm_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id)
);

CREATE TABLE attendance_marks (
  id           uuid PRIMARY KEY,
  tenant_id    uuid NOT NULL REFERENCES tenants (id),
  batch_id     uuid NOT NULL,
  customer_id  uuid NOT NULL,
  marked_on    date NOT NULL,
  status       text NOT NULL CHECK (status IN ('PRESENT','ABSENT')),
  created_at   timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_attendance UNIQUE (batch_id, customer_id, marked_on),
  CONSTRAINT fk_att_batch FOREIGN KEY (tenant_id, batch_id) REFERENCES batches (tenant_id, id),
  CONSTRAINT fk_att_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id)
);

ALTER TABLE batches ENABLE ROW LEVEL SECURITY;
ALTER TABLE batch_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_marks ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON batches
  USING (tenant_id = app_tenant_id() OR app_is_platform());
CREATE POLICY tenant_isolation ON batch_members
  USING (tenant_id = app_tenant_id() OR app_is_platform());
CREATE POLICY tenant_isolation ON attendance_marks
  USING (tenant_id = app_tenant_id() OR app_is_platform());

INSERT INTO role_permissions (role_code, permission_code) VALUES
  ('STAFF', 'payments.record'),
  ('STAFF', 'payments.view'),
  ('STAFF', 'attendance.manage'),
  ('STAFF', 'batches.manage'),
  ('STAFF', 'imports.run')
ON CONFLICT DO NOTHING;

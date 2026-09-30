ALTER TABLE tenants
  ADD COLUMN display_name text,
  ADD COLUMN accent_color text,
  ADD COLUMN trial_ends_at timestamptz,
  ADD COLUMN grace_days int NOT NULL DEFAULT 7,
  ADD COLUMN billing_status text NOT NULL DEFAULT 'OK'
    CHECK (billing_status IN ('OK', 'PAST_DUE', 'UNPAID')),
  ADD COLUMN custom_max_members int,
  ADD COLUMN custom_max_staff int;

ALTER TABLE tenants
  ADD CONSTRAINT chk_accent_color
  CHECK (accent_color IS NULL OR accent_color ~ '^#[0-9A-Fa-f]{6}$');

ALTER TABLE tenants
  ADD CONSTRAINT chk_custom_limits
  CHECK (
    (custom_max_members IS NULL OR custom_max_members >= 1)
    AND (custom_max_staff IS NULL OR custom_max_staff >= 0)
  );

CREATE TABLE tenant_notes (
  id          uuid PRIMARY KEY,
  tenant_id   uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
  body        text NOT NULL,
  created_by  uuid REFERENCES users (id) ON DELETE SET NULL,
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_tenant_notes_tenant ON tenant_notes (tenant_id, created_at DESC);

CREATE TABLE platform_invoices (
  id            uuid PRIMARY KEY,
  tenant_id     uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
  period_label  text NOT NULL,
  amount_minor  bigint NOT NULL CHECK (amount_minor >= 0),
  currency      char(3) NOT NULL DEFAULT 'INR',
  status        text NOT NULL DEFAULT 'DUE'
                CHECK (status IN ('DUE', 'PAID', 'VOID')),
  due_on        date,
  paid_at       timestamptz,
  note          text,
  created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_platform_invoices_tenant ON platform_invoices (tenant_id, created_at DESC);

ALTER TABLE tenant_notes ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform_invoices ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON tenant_notes
  USING (tenant_id = app_tenant_id() OR app_is_platform());
CREATE POLICY tenant_isolation ON platform_invoices
  USING (tenant_id = app_tenant_id() OR app_is_platform());

UPDATE tenants
   SET trial_ends_at = now() + interval '14 days'
 WHERE plan_code = 'PRO' AND trial_ends_at IS NULL;

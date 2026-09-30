CREATE TABLE reminder_templates (
  tenant_id       uuid PRIMARY KEY REFERENCES tenants (id) ON DELETE CASCADE,
  body            text        NOT NULL,
  attach_logo     boolean     NOT NULL DEFAULT false,
  attach_scanner  boolean     NOT NULL DEFAULT false,
  updated_at      timestamptz NOT NULL DEFAULT now(),
  version         bigint      NOT NULL DEFAULT 0
);

CREATE TABLE tenant_media (
  tenant_id     uuid        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
  kind          text        NOT NULL CHECK (kind IN ('LOGO', 'SCANNER')),
  content_type  text        NOT NULL,
  bytes         bytea       NOT NULL,
  updated_at    timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, kind)
);

ALTER TABLE reminder_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_media ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON reminder_templates
  USING (tenant_id = app_tenant_id() OR app_is_platform());
CREATE POLICY tenant_isolation ON tenant_media
  USING (tenant_id = app_tenant_id() OR app_is_platform());

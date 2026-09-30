-- Owner-side customers. tenant_id is stamped by the app; RLS rejects the wrong tenant.
CREATE TABLE customers (
  id            uuid PRIMARY KEY,
  tenant_id     uuid        NOT NULL REFERENCES tenants (id),
  customer_code text        NOT NULL,
  full_name     text        NOT NULL,
  phone         text,
  email         text,
  status        text        NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE', 'INACTIVE')),
  notes         text,
  custom_data   jsonb       NOT NULL DEFAULT '{}'::jsonb,
  created_by    uuid        REFERENCES users (id) ON DELETE SET NULL,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  deleted_at    timestamptz,
  version       bigint      NOT NULL DEFAULT 0,
  CONSTRAINT uq_customers_tenant_id UNIQUE (tenant_id, id),
  CONSTRAINT uq_customers_code UNIQUE (tenant_id, customer_code),
  CONSTRAINT ck_customers_identifier CHECK (phone IS NOT NULL OR email IS NOT NULL)
);

CREATE UNIQUE INDEX uq_customers_phone
  ON customers (tenant_id, phone)
  WHERE phone IS NOT NULL AND deleted_at IS NULL;
CREATE UNIQUE INDEX uq_customers_email
  ON customers (tenant_id, lower(email))
  WHERE email IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX ix_customers_tenant_name ON customers (tenant_id, full_name);
CREATE INDEX ix_customers_name_trgm ON customers USING gin (full_name gin_trgm_ops);

ALTER TABLE customers ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON customers
  USING (tenant_id = app_tenant_id() OR app_is_platform());

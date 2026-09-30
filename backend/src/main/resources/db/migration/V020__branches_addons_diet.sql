-- Locations, product extras (protein, diet packs), and diet-chart templates.
-- Independent of membership fee rows. WhatsApp send is click-to-chat, not a scheduler.

CREATE TABLE tenant_branches (
  id          uuid PRIMARY KEY,
  tenant_id   uuid NOT NULL REFERENCES tenants (id),
  name        text NOT NULL,
  address     text,
  phone       text,
  is_primary  boolean NOT NULL DEFAULT false,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now(),
  deleted_at  timestamptz,
  version     bigint NOT NULL DEFAULT 0,
  CONSTRAINT uq_tenant_branches_id UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX uq_tenant_branches_name
  ON tenant_branches (tenant_id, lower(name))
  WHERE deleted_at IS NULL;

ALTER TABLE customers
  ADD COLUMN branch_id uuid;

ALTER TABLE customers
  ADD CONSTRAINT fk_customers_branch
  FOREIGN KEY (tenant_id, branch_id) REFERENCES tenant_branches (tenant_id, id);

CREATE TABLE addon_products (
  id            uuid PRIMARY KEY,
  tenant_id     uuid NOT NULL REFERENCES tenants (id),
  name          text NOT NULL,
  description   text,
  amount_minor  bigint NOT NULL CHECK (amount_minor >= 0),
  currency      char(3) NOT NULL DEFAULT 'INR',
  active        boolean NOT NULL DEFAULT true,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  deleted_at    timestamptz,
  version       bigint NOT NULL DEFAULT 0,
  CONSTRAINT uq_addon_products_id UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX uq_addon_products_name
  ON addon_products (tenant_id, lower(name))
  WHERE deleted_at IS NULL;

CREATE TABLE addon_sales (
  id            uuid PRIMARY KEY,
  tenant_id     uuid NOT NULL REFERENCES tenants (id),
  product_id    uuid NOT NULL,
  customer_id   uuid NOT NULL,
  amount_minor  bigint NOT NULL CHECK (amount_minor >= 0),
  currency      char(3) NOT NULL DEFAULT 'INR',
  method        text NOT NULL DEFAULT 'CASH' CHECK (method IN ('CASH', 'UPI', 'CARD', 'OTHER')),
  notes         text,
  sold_on       date NOT NULL DEFAULT CURRENT_DATE,
  created_by    uuid REFERENCES users (id) ON DELETE SET NULL,
  created_at    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT fk_addon_sales_product FOREIGN KEY (tenant_id, product_id) REFERENCES addon_products (tenant_id, id),
  CONSTRAINT fk_addon_sales_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id)
);

CREATE INDEX ix_addon_sales_tenant_on ON addon_sales (tenant_id, sold_on DESC);

CREATE TABLE diet_chart_templates (
  id          uuid PRIMARY KEY,
  tenant_id   uuid NOT NULL REFERENCES tenants (id),
  name        text NOT NULL,
  body        text NOT NULL,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now(),
  deleted_at  timestamptz,
  version     bigint NOT NULL DEFAULT 0,
  CONSTRAINT uq_diet_chart_templates_id UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX uq_diet_chart_templates_name
  ON diet_chart_templates (tenant_id, lower(name))
  WHERE deleted_at IS NULL;

CREATE TABLE diet_chart_sends (
  id           uuid PRIMARY KEY,
  tenant_id    uuid NOT NULL REFERENCES tenants (id),
  template_id  uuid NOT NULL,
  customer_id  uuid NOT NULL,
  channel      text NOT NULL,
  body         text NOT NULL,
  created_by   uuid REFERENCES users (id) ON DELETE SET NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT fk_diet_sends_template FOREIGN KEY (tenant_id, template_id) REFERENCES diet_chart_templates (tenant_id, id),
  CONSTRAINT fk_diet_sends_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id)
);

ALTER TABLE tenant_branches ENABLE ROW LEVEL SECURITY;
ALTER TABLE addon_products ENABLE ROW LEVEL SECURITY;
ALTER TABLE addon_sales ENABLE ROW LEVEL SECURITY;
ALTER TABLE diet_chart_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE diet_chart_sends ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON tenant_branches
  USING (tenant_id = app_tenant_id() OR app_is_platform());
CREATE POLICY tenant_isolation ON addon_products
  USING (tenant_id = app_tenant_id() OR app_is_platform());
CREATE POLICY tenant_isolation ON addon_sales
  USING (tenant_id = app_tenant_id() OR app_is_platform());
CREATE POLICY tenant_isolation ON diet_chart_templates
  USING (tenant_id = app_tenant_id() OR app_is_platform());
CREATE POLICY tenant_isolation ON diet_chart_sends
  USING (tenant_id = app_tenant_id() OR app_is_platform());

UPDATE business_type_presets
   SET modules = '["CUSTOMERS","FEES","PAYMENTS","MEMBERSHIP","ATTENDANCE","NOTIFICATIONS","REPORTS","SETTINGS","BRANCHES","ADDONS","DIET_CHARTS"]'::jsonb
 WHERE code = 'GYM';

UPDATE business_type_presets
   SET modules = modules || '["BRANCHES","ADDONS"]'::jsonb
 WHERE code IN ('ACADEMY', 'TUITION', 'GENERIC');

INSERT INTO tenant_modules (tenant_id, module_code, enabled)
SELECT t.id, m.code, true
  FROM tenants t
  CROSS JOIN (VALUES ('BRANCHES'), ('ADDONS'), ('DIET_CHARTS')) AS m(code)
 WHERE t.business_type = 'GYM'
ON CONFLICT (tenant_id, module_code) DO UPDATE SET enabled = true;

INSERT INTO tenant_modules (tenant_id, module_code, enabled)
SELECT t.id, m.code, true
  FROM tenants t
  CROSS JOIN (VALUES ('BRANCHES'), ('ADDONS')) AS m(code)
 WHERE t.business_type IN ('ACADEMY', 'TUITION', 'GENERIC')
ON CONFLICT (tenant_id, module_code) DO NOTHING;

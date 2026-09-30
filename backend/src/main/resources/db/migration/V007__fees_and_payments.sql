-- Recurring plans, generated dues, and immutable payments (allocate to a fee).
CREATE TABLE fee_plans (
  id              uuid PRIMARY KEY,
  tenant_id       uuid        NOT NULL REFERENCES tenants (id),
  name            text        NOT NULL,
  amount_minor    bigint      NOT NULL CHECK (amount_minor >= 0),
  currency        char(3)     NOT NULL DEFAULT 'INR',
  billing_cycle   text        NOT NULL DEFAULT 'MONTHLY'
                  CHECK (billing_cycle IN ('WEEKLY','MONTHLY','QUARTERLY','HALF_YEARLY','ANNUAL')),
  cycle_interval  int         NOT NULL DEFAULT 1 CHECK (cycle_interval >= 1),
  due_rule        text        NOT NULL DEFAULT 'ON_DUE_DATE',
  due_day         int,
  grace_days      int         NOT NULL DEFAULT 0 CHECK (grace_days >= 0),
  is_default      boolean     NOT NULL DEFAULT false,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  deleted_at      timestamptz,
  version         bigint      NOT NULL DEFAULT 0,
  CONSTRAINT uq_fee_plans_tenant_id UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX uq_fee_plans_default
  ON fee_plans (tenant_id)
  WHERE is_default AND deleted_at IS NULL;

CREATE TABLE customer_fee_plans (
  id                   uuid PRIMARY KEY,
  tenant_id            uuid        NOT NULL REFERENCES tenants (id),
  customer_id          uuid        NOT NULL,
  fee_plan_id          uuid        NOT NULL,
  amount_override_minor bigint,
  billing_start        date        NOT NULL,
  billing_end          date,
  status               text        NOT NULL DEFAULT 'ACTIVE'
                       CHECK (status IN ('ACTIVE','PAUSED','ENDED')),
  generated_through    date,
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now(),
  version              bigint      NOT NULL DEFAULT 0,
  CONSTRAINT uq_cfp_tenant_id UNIQUE (tenant_id, id),
  CONSTRAINT fk_cfp_customer FOREIGN KEY (tenant_id, customer_id)
    REFERENCES customers (tenant_id, id),
  CONSTRAINT fk_cfp_plan FOREIGN KEY (tenant_id, fee_plan_id)
    REFERENCES fee_plans (tenant_id, id)
);

CREATE UNIQUE INDEX uq_cfp_one_active
  ON customer_fee_plans (tenant_id, customer_id)
  WHERE status = 'ACTIVE';

CREATE TABLE fees (
  id                    uuid PRIMARY KEY,
  tenant_id             uuid        NOT NULL REFERENCES tenants (id),
  customer_id           uuid        NOT NULL,
  customer_fee_plan_id  uuid        NOT NULL,
  period_start          date        NOT NULL,
  period_end            date        NOT NULL,
  due_date              date        NOT NULL,
  grace_days            int         NOT NULL DEFAULT 0,
  gross_minor           bigint      NOT NULL CHECK (gross_minor >= 0),
  adjustments_minor     bigint      NOT NULL DEFAULT 0,
  paid_minor            bigint      NOT NULL DEFAULT 0,
  currency              char(3)     NOT NULL DEFAULT 'INR',
  status                text        NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','PARTIALLY_PAID','PAID','CANCELLED')),
  created_at            timestamptz NOT NULL DEFAULT now(),
  updated_at            timestamptz NOT NULL DEFAULT now(),
  deleted_at            timestamptz,
  version               bigint      NOT NULL DEFAULT 0,
  CONSTRAINT uq_fees_tenant_id UNIQUE (tenant_id, id),
  CONSTRAINT fk_fees_customer FOREIGN KEY (tenant_id, customer_id)
    REFERENCES customers (tenant_id, id),
  CONSTRAINT fk_fees_cfp FOREIGN KEY (tenant_id, customer_fee_plan_id)
    REFERENCES customer_fee_plans (tenant_id, id),
  CONSTRAINT ck_fees_paid CHECK (paid_minor >= 0 AND paid_minor <= gross_minor + adjustments_minor),
  CONSTRAINT uq_fees_period UNIQUE (customer_fee_plan_id, period_start)
);

CREATE INDEX ix_fees_pending
  ON fees (tenant_id, due_date)
  WHERE status IN ('PENDING','PARTIALLY_PAID') AND deleted_at IS NULL;
CREATE INDEX ix_fees_customer
  ON fees (tenant_id, customer_id, period_start DESC);

CREATE TABLE payments (
  id              uuid PRIMARY KEY,
  tenant_id       uuid        NOT NULL REFERENCES tenants (id),
  customer_id     uuid        NOT NULL,
  amount_minor    bigint      NOT NULL CHECK (amount_minor > 0),
  currency        char(3)     NOT NULL DEFAULT 'INR',
  method          text        NOT NULL
                  CHECK (method IN ('CASH','UPI','CARD','BANK','OTHER')),
  reference_no    text,
  receipt_no      text        NOT NULL,
  paid_on         date        NOT NULL DEFAULT CURRENT_DATE,
  status          text        NOT NULL DEFAULT 'RECORDED'
                  CHECK (status IN ('RECORDED','VOID')),
  created_by      uuid        REFERENCES users (id) ON DELETE SET NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_payments_tenant_id UNIQUE (tenant_id, id),
  CONSTRAINT uq_payments_receipt UNIQUE (tenant_id, receipt_no),
  CONSTRAINT fk_payments_customer FOREIGN KEY (tenant_id, customer_id)
    REFERENCES customers (tenant_id, id)
);

CREATE TABLE payment_allocations (
  id           uuid PRIMARY KEY,
  tenant_id    uuid   NOT NULL REFERENCES tenants (id),
  payment_id   uuid   NOT NULL,
  fee_id       uuid   NOT NULL,
  amount_minor bigint NOT NULL CHECK (amount_minor > 0),
  CONSTRAINT uq_alloc_payment_fee UNIQUE (payment_id, fee_id),
  CONSTRAINT fk_alloc_payment FOREIGN KEY (tenant_id, payment_id)
    REFERENCES payments (tenant_id, id),
  CONSTRAINT fk_alloc_fee FOREIGN KEY (tenant_id, fee_id)
    REFERENCES fees (tenant_id, id)
);

ALTER TABLE fee_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE customer_fee_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE fees ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_allocations ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON fee_plans
  USING (tenant_id = app_tenant_id() OR app_is_platform());
CREATE POLICY tenant_isolation ON customer_fee_plans
  USING (tenant_id = app_tenant_id() OR app_is_platform());
CREATE POLICY tenant_isolation ON fees
  USING (tenant_id = app_tenant_id() OR app_is_platform());
CREATE POLICY tenant_isolation ON payments
  USING (tenant_id = app_tenant_id() OR app_is_platform());
CREATE POLICY tenant_isolation ON payment_allocations
  USING (tenant_id = app_tenant_id() OR app_is_platform());

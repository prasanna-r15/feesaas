CREATE UNIQUE INDEX uq_fee_plans_name
  ON fee_plans (tenant_id, lower(name))
  WHERE deleted_at IS NULL;

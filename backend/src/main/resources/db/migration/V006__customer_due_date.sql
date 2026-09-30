ALTER TABLE customers ADD COLUMN due_date date;
CREATE INDEX ix_customers_due ON customers (tenant_id, due_date) WHERE deleted_at IS NULL;

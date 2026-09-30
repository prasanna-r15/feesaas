CREATE TABLE personal_expense_categories (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id uuid NOT NULL REFERENCES personal_workspaces (id) ON DELETE CASCADE,
  name         text NOT NULL,
  icon         text NOT NULL DEFAULT 'category',
  color        text NOT NULL DEFAULT '#0F766E',
  system_key   text,
  active       boolean NOT NULL DEFAULT true,
  created_at   timestamptz NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, name)
);

CREATE TABLE personal_expenses (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id uuid NOT NULL REFERENCES personal_workspaces (id) ON DELETE CASCADE,
  category_id  uuid NOT NULL REFERENCES personal_expense_categories (id),
  amount_minor bigint NOT NULL CHECK (amount_minor > 0),
  currency     char(3) NOT NULL DEFAULT 'INR',
  description  text,
  occurred_on  date NOT NULL DEFAULT (CURRENT_DATE),
  method       text,
  client_id    uuid,
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_personal_expense_client ON personal_expenses (workspace_id, client_id) WHERE client_id IS NOT NULL;
CREATE INDEX ix_personal_expenses_ws_date ON personal_expenses (workspace_id, occurred_on DESC);

CREATE TABLE personal_income (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id uuid NOT NULL REFERENCES personal_workspaces (id) ON DELETE CASCADE,
  amount_minor bigint NOT NULL CHECK (amount_minor > 0),
  currency     char(3) NOT NULL DEFAULT 'INR',
  source       text NOT NULL,
  description  text,
  occurred_on  date NOT NULL DEFAULT (CURRENT_DATE),
  client_id    uuid,
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_personal_income_client ON personal_income (workspace_id, client_id) WHERE client_id IS NOT NULL;

CREATE TABLE personal_budgets (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id uuid NOT NULL REFERENCES personal_workspaces (id) ON DELETE CASCADE,
  category_id  uuid NOT NULL REFERENCES personal_expense_categories (id),
  year_month   char(7) NOT NULL,
  limit_minor  bigint NOT NULL CHECK (limit_minor > 0),
  currency     char(3) NOT NULL DEFAULT 'INR',
  created_at   timestamptz NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, category_id, year_month)
);

CREATE TABLE user_alerts (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  kind       text NOT NULL,
  payload    jsonb NOT NULL DEFAULT '{}'::jsonb,
  read_at    timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE FUNCTION seed_personal_categories(p_workspace uuid) RETURNS int
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
BEGIN
  INSERT INTO personal_expense_categories (workspace_id, name, icon, color, system_key)
  VALUES
    (p_workspace, 'Food', 'restaurant', '#EA580C', 'FOOD'),
    (p_workspace, 'Transport', 'directions_car', '#2563EB', 'TRANSPORT'),
    (p_workspace, 'Shopping', 'shopping_bag', '#DB2777', 'SHOPPING'),
    (p_workspace, 'Bills', 'receipt', '#7C3AED', 'BILLS'),
    (p_workspace, 'Entertainment', 'movie', '#CA8A04', 'ENTERTAINMENT'),
    (p_workspace, 'Health', 'favorite', '#DC2626', 'HEALTH'),
    (p_workspace, 'Education', 'school', '#0891B2', 'EDUCATION'),
    (p_workspace, 'Travel', 'flight', '#0F766E', 'TRAVEL'),
    (p_workspace, 'Rent', 'home', '#4B5563', 'RENT'),
    (p_workspace, 'Subscriptions', 'subscriptions', '#4F46E5', 'SUBSCRIPTIONS'),
    (p_workspace, 'Family', 'family_restroom', '#9333EA', 'FAMILY'),
    (p_workspace, 'Other', 'more_horiz', '#6B7280', 'OTHER')
  ON CONFLICT (workspace_id, name) DO NOTHING;
  RETURN 12;
END;
$$;
REVOKE ALL ON FUNCTION seed_personal_categories(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION seed_personal_categories(uuid) TO feesaas_app;

ALTER TABLE personal_expense_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE personal_expenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE personal_income ENABLE ROW LEVEL SECURITY;
ALTER TABLE personal_budgets ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_alerts ENABLE ROW LEVEL SECURITY;

CREATE POLICY ws_owner ON personal_expense_categories
  USING (workspace_id IN (SELECT id FROM personal_workspaces WHERE owner_user_id = app_user_id()));
CREATE POLICY ws_owner ON personal_expenses
  USING (workspace_id IN (SELECT id FROM personal_workspaces WHERE owner_user_id = app_user_id()));
CREATE POLICY ws_owner ON personal_income
  USING (workspace_id IN (SELECT id FROM personal_workspaces WHERE owner_user_id = app_user_id()));
CREATE POLICY ws_owner ON personal_budgets
  USING (workspace_id IN (SELECT id FROM personal_workspaces WHERE owner_user_id = app_user_id()));
CREATE POLICY owner_only ON user_alerts
  USING (user_id = app_user_id());

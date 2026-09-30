CREATE TABLE expense_groups (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name        text NOT NULL,
  group_type  text NOT NULL DEFAULT 'CUSTOM',
  created_by  uuid NOT NULL REFERENCES users (id),
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE user_memberships
  ADD CONSTRAINT fk_membership_group FOREIGN KEY (group_id) REFERENCES expense_groups (id) ON DELETE CASCADE;
CREATE UNIQUE INDEX uq_membership_group ON user_memberships (user_id, group_id) WHERE kind = 'GROUP';

CREATE TABLE expense_group_members (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id     uuid NOT NULL REFERENCES expense_groups (id) ON DELETE CASCADE,
  user_id      uuid REFERENCES users (id) ON DELETE SET NULL,
  display_name text NOT NULL,
  status       text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LEFT')),
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_group_member_user ON expense_group_members (group_id, user_id) WHERE user_id IS NOT NULL;

CREATE TABLE group_expenses (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id     uuid NOT NULL REFERENCES expense_groups (id) ON DELETE CASCADE,
  description  text NOT NULL,
  amount_minor bigint NOT NULL CHECK (amount_minor > 0),
  currency     char(3) NOT NULL DEFAULT 'INR',
  paid_by      uuid NOT NULL REFERENCES expense_group_members (id),
  split_method text NOT NULL CHECK (split_method IN ('EQUAL', 'EXACT', 'PERCENT', 'SHARES')),
  occurred_on  date NOT NULL DEFAULT (CURRENT_DATE),
  created_by   uuid NOT NULL REFERENCES users (id),
  created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE group_expense_shares (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  expense_id  uuid NOT NULL REFERENCES group_expenses (id) ON DELETE CASCADE,
  member_id   uuid NOT NULL REFERENCES expense_group_members (id),
  share_minor bigint NOT NULL CHECK (share_minor >= 0),
  weight      numeric,
  UNIQUE (expense_id, member_id)
);

CREATE TABLE group_settlements (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id     uuid NOT NULL REFERENCES expense_groups (id) ON DELETE CASCADE,
  payer_id     uuid NOT NULL REFERENCES expense_group_members (id),
  receiver_id  uuid NOT NULL REFERENCES expense_group_members (id),
  amount_minor bigint NOT NULL CHECK (amount_minor > 0),
  currency     char(3) NOT NULL DEFAULT 'INR',
  method       text,
  notes        text,
  occurred_on  date NOT NULL DEFAULT (CURRENT_DATE),
  created_by   uuid NOT NULL REFERENCES users (id),
  created_at   timestamptz NOT NULL DEFAULT now(),
  CHECK (payer_id <> receiver_id)
);

CREATE TABLE group_invitations (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id    uuid NOT NULL REFERENCES expense_groups (id) ON DELETE CASCADE,
  token_hash  text NOT NULL UNIQUE,
  email       text,
  phone       text,
  created_by  uuid NOT NULL REFERENCES users (id),
  expires_at  timestamptz NOT NULL,
  used_at     timestamptz,
  revoked_at  timestamptz,
  created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE group_activity (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id   uuid NOT NULL REFERENCES expense_groups (id) ON DELETE CASCADE,
  actor_id   uuid REFERENCES users (id),
  verb       text NOT NULL,
  summary    text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE expense_groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE expense_group_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE group_expenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE group_expense_shares ENABLE ROW LEVEL SECURITY;
ALTER TABLE group_settlements ENABLE ROW LEVEL SECURITY;
ALTER TABLE group_invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE group_activity ENABLE ROW LEVEL SECURITY;

CREATE POLICY member_read ON expense_groups
  USING (created_by = app_user_id()
         OR id IN (SELECT um.group_id FROM user_memberships um WHERE um.user_id = app_user_id() AND um.kind = 'GROUP'));
CREATE POLICY member_read ON expense_group_members
  USING (user_id = app_user_id()
         OR group_id IN (SELECT um.group_id FROM user_memberships um WHERE um.user_id = app_user_id() AND um.kind = 'GROUP')
         OR group_id IN (SELECT g.id FROM expense_groups g WHERE g.created_by = app_user_id()));
CREATE POLICY member_read ON group_expenses
  USING (group_id IN (SELECT um.group_id FROM user_memberships um WHERE um.user_id = app_user_id() AND um.kind = 'GROUP'));
CREATE POLICY member_read ON group_expense_shares
  USING (expense_id IN (
    SELECT e.id FROM group_expenses e
    WHERE e.group_id IN (SELECT um.group_id FROM user_memberships um WHERE um.user_id = app_user_id() AND um.kind = 'GROUP')));
CREATE POLICY member_read ON group_settlements
  USING (group_id IN (SELECT um.group_id FROM user_memberships um WHERE um.user_id = app_user_id() AND um.kind = 'GROUP'));
CREATE POLICY member_read ON group_invitations
  USING (group_id IN (SELECT um.group_id FROM user_memberships um WHERE um.user_id = app_user_id() AND um.kind = 'GROUP'));
CREATE POLICY member_read ON group_activity
  USING (group_id IN (SELECT um.group_id FROM user_memberships um WHERE um.user_id = app_user_id() AND um.kind = 'GROUP'));

CREATE OR REPLACE FUNCTION create_expense_group(p_user_id uuid, p_name text, p_type text, p_creator_name text)
RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  v_group uuid;
BEGIN
  INSERT INTO expense_groups (id, name, group_type, created_by)
  VALUES (gen_random_uuid(), p_name, p_type, p_user_id)
  RETURNING id INTO v_group;
  INSERT INTO expense_group_members (id, group_id, user_id, display_name, status)
  VALUES (gen_random_uuid(), v_group, p_user_id, p_creator_name, 'ACTIVE');
  INSERT INTO user_memberships (id, user_id, kind, role_code, group_id)
  VALUES (gen_random_uuid(), p_user_id, 'GROUP', 'MEMBER', v_group);
  INSERT INTO group_activity (id, group_id, actor_id, verb, summary)
  VALUES (gen_random_uuid(), v_group, p_user_id, 'CREATED', p_creator_name || ' created ' || p_name);
  RETURN v_group;
END;
$$;
REVOKE ALL ON FUNCTION create_expense_group(uuid, text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION create_expense_group(uuid, text, text, text) TO feesaas_app;

CREATE OR REPLACE FUNCTION auth_accept_group_invite(p_token_hash text, p_user_id uuid, p_name text)
RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  v_inv group_invitations%ROWTYPE;
  v_member uuid;
BEGIN
  SELECT * INTO v_inv FROM group_invitations
   WHERE token_hash = p_token_hash
     AND revoked_at IS NULL
     AND used_at IS NULL
     AND expires_at > now();
  IF NOT FOUND THEN
    RAISE EXCEPTION 'invite_invalid';
  END IF;
  UPDATE group_invitations SET used_at = now() WHERE id = v_inv.id;
  INSERT INTO expense_group_members (id, group_id, user_id, display_name, status)
  VALUES (gen_random_uuid(), v_inv.group_id, p_user_id, p_name, 'ACTIVE')
  ON CONFLICT DO NOTHING
  RETURNING id INTO v_member;
  IF v_member IS NULL THEN
    SELECT id INTO v_member FROM expense_group_members WHERE group_id = v_inv.group_id AND user_id = p_user_id;
    UPDATE expense_group_members SET status = 'ACTIVE', display_name = p_name WHERE id = v_member;
  END IF;
  INSERT INTO user_memberships (id, user_id, kind, role_code, group_id)
  VALUES (gen_random_uuid(), p_user_id, 'GROUP', 'GROUP_MEMBER', v_inv.group_id)
  ON CONFLICT DO NOTHING;
  RETURN v_inv.group_id;
END;
$$;
REVOKE ALL ON FUNCTION auth_accept_group_invite(text, uuid, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth_accept_group_invite(text, uuid, text) TO feesaas_app;

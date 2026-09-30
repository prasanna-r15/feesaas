ALTER TABLE group_expense_shares
  ADD COLUMN IF NOT EXISTS settled_at timestamptz,
  ADD COLUMN IF NOT EXISTS settled_by uuid REFERENCES users (id) ON DELETE SET NULL;

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

CREATE OR REPLACE FUNCTION public_group_invite_preview(p_token_hash text)
RETURNS TABLE (
  group_id uuid,
  group_name text,
  group_type text,
  member_count bigint,
  invited_by text
)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
AS $$
BEGIN
  RETURN QUERY
  SELECT g.id,
         g.name,
         g.group_type,
         (SELECT count(*) FROM expense_group_members m WHERE m.group_id = g.id AND m.status = 'ACTIVE'),
         coalesce(u.full_name, 'A member')
    FROM group_invitations i
    JOIN expense_groups g ON g.id = i.group_id
    LEFT JOIN users u ON u.id = i.created_by
   WHERE i.token_hash = p_token_hash
     AND i.revoked_at IS NULL
     AND i.used_at IS NULL
     AND i.expires_at > now();
END;
$$;

REVOKE ALL ON FUNCTION public_group_invite_preview(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public_group_invite_preview(text) TO feesaas_app;
GRANT EXECUTE ON FUNCTION auth_accept_group_invite(text, uuid, text) TO feesaas_app;

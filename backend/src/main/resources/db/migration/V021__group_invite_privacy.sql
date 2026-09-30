DROP POLICY IF EXISTS member_read ON expense_groups;
CREATE POLICY member_read ON expense_groups
  USING (
    id IN (
      SELECT um.group_id
        FROM user_memberships um
       WHERE um.user_id = app_user_id()
         AND um.kind = 'GROUP'
    )
  );

DROP POLICY IF EXISTS member_read ON expense_group_members;
CREATE POLICY member_read ON expense_group_members
  USING (
    group_id IN (
      SELECT um.group_id
        FROM user_memberships um
       WHERE um.user_id = app_user_id()
         AND um.kind = 'GROUP'
    )
  );

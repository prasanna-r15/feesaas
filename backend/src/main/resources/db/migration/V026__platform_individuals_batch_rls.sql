DROP POLICY IF EXISTS tenant_isolation ON batch_members;
CREATE POLICY tenant_isolation ON batch_members
  USING (tenant_id = app_tenant_id() OR app_is_platform())
  WITH CHECK (tenant_id = app_tenant_id() OR app_is_platform());

DROP POLICY IF EXISTS owner_only ON personal_workspaces;
CREATE POLICY owner_only ON personal_workspaces
  USING (owner_user_id = app_user_id() OR app_is_platform());

DROP POLICY IF EXISTS owner_only ON user_memberships;
CREATE POLICY owner_only ON user_memberships
  USING (user_id = app_user_id() OR app_is_platform());

DROP POLICY IF EXISTS member_read ON expense_group_members;
CREATE POLICY member_read ON expense_group_members
  USING (
    app_is_platform()
    OR group_id IN (
      SELECT um.group_id
        FROM user_memberships um
       WHERE um.user_id = app_user_id()
         AND um.kind = 'GROUP'
    )
  );

DROP POLICY IF EXISTS ws_owner ON personal_expenses;
CREATE POLICY ws_owner ON personal_expenses
  USING (
    app_is_platform()
    OR workspace_id IN (SELECT id FROM personal_workspaces WHERE owner_user_id = app_user_id())
  );

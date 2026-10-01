CREATE TABLE IF NOT EXISTS support_threads (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    uuid NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS support_messages (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  thread_id      uuid NOT NULL REFERENCES support_threads (id) ON DELETE CASCADE,
  author_user_id uuid REFERENCES users (id) ON DELETE SET NULL,
  from_platform  boolean NOT NULL,
  body           text NOT NULL,
  created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_support_messages_thread ON support_messages (thread_id, created_at);

ALTER TABLE support_threads ENABLE ROW LEVEL SECURITY;
ALTER TABLE support_messages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS own_or_platform ON support_threads;
CREATE POLICY own_or_platform ON support_threads
  USING (user_id = app_user_id() OR app_is_platform())
  WITH CHECK (user_id = app_user_id() OR app_is_platform());

DROP POLICY IF EXISTS own_or_platform ON support_messages;
CREATE POLICY own_or_platform ON support_messages
  USING (
    app_is_platform()
    OR EXISTS (
      SELECT 1 FROM support_threads t
       WHERE t.id = support_messages.thread_id
         AND t.user_id = app_user_id()
    )
  )
  WITH CHECK (
    app_is_platform()
    OR EXISTS (
      SELECT 1 FROM support_threads t
       WHERE t.id = support_messages.thread_id
         AND t.user_id = app_user_id()
    )
  );

INSERT INTO platform_config (param_key, param_sub_key, param_value, description, locked) VALUES
(
  'PASSWORD_OTP_SUBJECT',
  'DEFAULT',
  'Your DueMate password reset code',
  'Subject for forgot-password OTP email. Placeholders: {{name}} {{otp}}',
  true
),
(
  'PASSWORD_OTP_BODY',
  'DEFAULT',
  '<p>Hi {{name}},</p><p>Your password reset code is <b>{{otp}}</b>.</p><p>It expires in 10 minutes.</p>',
  'HTML body for forgot-password OTP email.',
  true
)
ON CONFLICT (param_key, param_sub_key) DO NOTHING;

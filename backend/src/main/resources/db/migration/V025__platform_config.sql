-- Platform key/value config. Looked up by param_key + param_sub_key (customer type), with DEFAULT fallback.
CREATE TABLE platform_config (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  param_key      text NOT NULL,
  param_sub_key  text NOT NULL DEFAULT 'DEFAULT',
  param_value    text NOT NULL,
  description    text,
  locked         boolean NOT NULL DEFAULT false,
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_platform_config_key UNIQUE (param_key, param_sub_key)
);

CREATE INDEX ix_platform_config_key ON platform_config (param_key);

INSERT INTO platform_config (param_key, param_sub_key, param_value, description, locked) VALUES
(
  'SIGNUP_OTP_SUBJECT',
  'DEFAULT',
  'Your DueMate signup code',
  'Subject for the signup OTP email. Placeholders: {{name}}',
  true
),
(
  'SIGNUP_OTP_BODY',
  'DEFAULT',
  '<p>Hi {{name}},</p><p>Your verification code is <b>{{otp}}</b>.</p><p>It expires in 10 minutes.</p>',
  'HTML body for the signup OTP email. Placeholders: {{name}} {{otp}}',
  true
),
(
  'DUES_EMAIL_SUBJECT',
  'DEFAULT',
  'Pending dues · {{tenantName}} · {{pendingCount}}',
  'Subject for the pending-dues digest. Placeholders: {{tenantName}} {{pendingCount}} {{total}} {{ownerName}}',
  true
),
(
  'DUES_EMAIL_BODY',
  'DEFAULT',
  '<p>Hi {{ownerName}},</p><p>{{tenantName}} currently has <b>{{pendingCount}}</b> pending dues totalling <b>{{total}}</b>.</p><table border=''1'' cellpadding=''6'' cellspacing=''0''><tr><th>Member</th><th>Due</th><th>Plan</th><th>Outstanding</th></tr>{{rows}}</table><p>This is an automatic DueMate digest.</p>',
  'HTML body for the pending-dues digest. Placeholders: {{ownerName}} {{tenantName}} {{pendingCount}} {{total}} {{rows}}',
  true
);

UPDATE signup_otp_challenges SET channel = 'EMAIL' WHERE channel <> 'EMAIL';
ALTER TABLE signup_otp_challenges DROP CONSTRAINT IF EXISTS signup_otp_challenges_channel_check;
ALTER TABLE signup_otp_challenges ADD CONSTRAINT signup_otp_challenges_channel_check CHECK (channel = 'EMAIL');

ALTER TABLE platform_mail_settings
  DROP COLUMN IF EXISTS sms_provider,
  DROP COLUMN IF EXISTS sms_api_key,
  DROP COLUMN IF EXISTS sms_sender;

ALTER TABLE platform_mail_settings
  ADD COLUMN sms_provider text NOT NULL DEFAULT 'TEXTBELT',
  ADD COLUMN sms_api_key text,
  ADD COLUMN sms_sender text;

-- Pending sign-ups before the user row exists. No RLS: looked up by secret challenge id.
CREATE TABLE signup_otp_challenges (
  id             uuid PRIMARY KEY,
  full_name      text NOT NULL,
  email          text,
  phone          text,
  password_hash  text NOT NULL,
  otp_hash       text NOT NULL,
  channel        text NOT NULL CHECK (channel IN ('EMAIL', 'SMS')),
  attempts       int NOT NULL DEFAULT 0,
  expires_at     timestamptz NOT NULL,
  used_at        timestamptz,
  created_at     timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_signup_otp_ident CHECK (email IS NOT NULL OR phone IS NOT NULL)
);

CREATE INDEX ix_signup_otp_open ON signup_otp_challenges (lower(coalesce(email, '')), coalesce(phone, ''))
  WHERE used_at IS NULL;

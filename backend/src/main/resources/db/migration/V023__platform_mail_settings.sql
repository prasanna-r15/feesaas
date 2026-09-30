CREATE TABLE platform_mail_settings (
  id              smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  enabled         boolean NOT NULL DEFAULT false,
  cron_expr       text NOT NULL DEFAULT '0 0 8 * * *',
  timezone        text NOT NULL DEFAULT 'Asia/Kolkata',
  smtp_host       text,
  smtp_port       int NOT NULL DEFAULT 587,
  smtp_username   text,
  smtp_password   text,
  smtp_from       text,
  last_run_at     timestamptz,
  last_result     text
);

INSERT INTO platform_mail_settings (id) VALUES (1);

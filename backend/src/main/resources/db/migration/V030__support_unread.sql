ALTER TABLE support_threads
  ADD COLUMN IF NOT EXISTS user_last_read_at timestamptz,
  ADD COLUMN IF NOT EXISTS platform_last_read_at timestamptz;

INSERT INTO platform_config (param_key, param_sub_key, param_value, description, locked) VALUES
(
  'BACKEND_KEEP_ALIVE_URL',
  'DEFAULT',
  'AUTO',
  'Public API origin to ping, e.g. https://your-service.onrender.com — or AUTO to use RENDER_EXTERNAL_URL. Must be the public hostname, not localhost, or Render will still sleep.',
  true
)
ON CONFLICT (param_key, param_sub_key) DO NOTHING;

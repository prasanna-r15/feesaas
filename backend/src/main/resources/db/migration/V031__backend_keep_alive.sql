INSERT INTO platform_config (param_key, param_sub_key, param_value, description, locked) VALUES
(
  'BACKEND_KEEP_ALIVE_INTERVAL_MINUTES',
  'DEFAULT',
  '10',
  'Minutes between backend keep-alive pings. Must be a whole number from 1 to 1440. Edit this in Config.',
  true
),
(
  'BACKEND_KEEP_ALIVE_ENABLED',
  'DEFAULT',
  'true',
  'Set to true to ping GET /api/health/keep-alive on the interval above. Set to false to disable keep-alive.',
  true
)
ON CONFLICT (param_key, param_sub_key) DO NOTHING;

ALTER TABLE tenant_settings
  ADD COLUMN logo_base64 text;

ALTER TABLE tenant_settings
  ADD CONSTRAINT chk_logo_base64_size
  CHECK (logo_base64 IS NULL OR char_length(logo_base64) <= 1600000);

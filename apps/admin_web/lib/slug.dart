String slugify(String raw) {
  final cleaned = raw
      .trim()
      .toLowerCase()
      .replaceAll(RegExp(r'[^a-z0-9]+'), '-')
      .replaceAll(RegExp(r'^-+|-+$'), '');
  if (cleaned.length <= 63) {
    return cleaned;
  }
  return cleaned.substring(0, 63).replaceAll(RegExp(r'-+$'), '');
}

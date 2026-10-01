String formatLocalDateTime(String? raw, {bool dateOnly = false}) {
  if (raw == null || raw.trim().isEmpty || raw == 'never' || raw == 'none') {
    return raw ?? '';
  }
  var value = raw.trim();
  if (value.endsWith('[]')) {
    value = value.substring(0, value.length - 2);
  }
  DateTime? parsed = DateTime.tryParse(value);
  if (parsed == null && value.contains(' ') && !value.contains('T')) {
    parsed = DateTime.tryParse(value.replaceFirst(' ', 'T'));
  }
  if (parsed == null) {
    return raw;
  }
  if (!parsed.isUtc &&
      !value.endsWith('Z') &&
      !value.contains('+') &&
      !RegExp(r'-\d{2}:\d{2}$').hasMatch(value)) {
    parsed = DateTime.tryParse('${value}Z') ?? parsed;
  }
  final local = parsed.toLocal();
  String two(int n) => n.toString().padLeft(2, '0');
  final day = '${local.year}-${two(local.month)}-${two(local.day)}';
  if (dateOnly || (value.length <= 10 && !value.contains('T'))) {
    return day;
  }
  return '$day ${two(local.hour)}:${two(local.minute)}';
}

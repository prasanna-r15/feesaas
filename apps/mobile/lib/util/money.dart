String rupees(int minor) {
  final value = minor / 100.0;
  if (value == value.roundToDouble()) {
    return '₹${value.toStringAsFixed(0)}';
  }
  return '₹${value.toStringAsFixed(2)}';
}

double? parseRupees(String raw) {
  var text = raw.trim().replaceAll(',', '').replaceAll('₹', '').replaceAll(' ', '');
  if (text.isEmpty) {
    return null;
  }
  return double.tryParse(text);
}

String localIsoDate([DateTime? now]) {
  final d = now ?? DateTime.now();
  String two(int n) => n.toString().padLeft(2, '0');
  return '${d.year}-${two(d.month)}-${two(d.day)}';
}

String localYearMonth([DateTime? now]) {
  final d = now ?? DateTime.now();
  return '${d.year}-${d.month.toString().padLeft(2, '0')}';
}

String rupees(int minor) {
  final value = minor / 100.0;
  if (value == value.roundToDouble()) {
    return '₹${value.toStringAsFixed(0)}';
  }
  return '₹${value.toStringAsFixed(2)}';
}

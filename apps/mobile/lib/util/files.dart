import 'package:file_picker/file_picker.dart';

export 'package:feesaas_mobile/util/file_save.dart';

class PickedCsv {
  PickedCsv({required this.name, required this.bytes});

  final String name;
  final List<int> bytes;
}

Future<PickedCsv?> pickCsv() async {
  final result = await FilePicker.platform.pickFiles(
    type: FileType.custom,
    allowedExtensions: const ['xlsx', 'csv'],
    withData: true,
  );
  if (result == null || result.files.isEmpty) {
    return null;
  }
  final picked = result.files.first;
  final bytes = picked.bytes;
  if (bytes == null) {
    return null;
  }
  return PickedCsv(name: picked.name, bytes: bytes);
}

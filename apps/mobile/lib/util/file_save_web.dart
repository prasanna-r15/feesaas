import 'dart:js_interop';
import 'dart:typed_data';

import 'package:web/web.dart';

Future<String> saveBytes(String filename, List<int> bytes) async {
  final blob = Blob(
    [Uint8List.fromList(bytes).toJS].toJS,
    BlobPropertyBag(type: _mime(filename)),
  );
  final url = URL.createObjectURL(blob);
  final body = document.body;
  if (body == null) {
    URL.revokeObjectURL(url);
    throw StateError('Cannot download the file in this browser.');
  }
  final anchor = HTMLAnchorElement()
    ..href = url
    ..download = filename;
  body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
  return filename;
}

String _mime(String filename) {
  final name = filename.toLowerCase();
  if (name.endsWith('.xlsx')) {
    return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
  }
  if (name.endsWith('.csv')) {
    return 'text/csv;charset=utf-8';
  }
  return 'application/octet-stream';
}

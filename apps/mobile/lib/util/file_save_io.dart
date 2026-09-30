import 'dart:io';

Future<String> saveBytes(String filename, List<int> bytes) async {
  final safe = filename.replaceAll(RegExp(r'[\\/:*?"<>|]'), '_');
  final dir = _downloadsDirectory();
  if (!dir.existsSync()) {
    dir.createSync(recursive: true);
  }
  final file = File('${dir.path}${Platform.pathSeparator}$safe');
  await file.writeAsBytes(bytes, flush: true);
  return file.path;
}

Directory _downloadsDirectory() {
  final home = Platform.environment['USERPROFILE'] ?? Platform.environment['HOME'];
  if (home != null && home.isNotEmpty) {
    return Directory('$home${Platform.pathSeparator}Downloads');
  }
  return Directory.systemTemp;
}

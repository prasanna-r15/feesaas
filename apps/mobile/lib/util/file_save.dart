export 'package:feesaas_mobile/util/file_save_stub.dart'
    if (dart.library.html) 'package:feesaas_mobile/util/file_save_web.dart'
    if (dart.library.js_interop) 'package:feesaas_mobile/util/file_save_web.dart'
    if (dart.library.io) 'package:feesaas_mobile/util/file_save_io.dart';

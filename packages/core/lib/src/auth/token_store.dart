import 'package:feesaas_core/src/auth/token_persist_stub.dart'
    if (dart.library.html) 'package:feesaas_core/src/auth/token_persist_web.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:uuid/uuid.dart';

class TokenStore {
  TokenStore({FlutterSecureStorage? storage})
      : _storage = storage ??
            const FlutterSecureStorage(
              webOptions: WebOptions(dbName: 'duemate', publicKey: 'duemate'),
            );

  static const _access = 'feesaas.access';
  static const _refresh = 'feesaas.refresh';
  static const _device = 'feesaas.device';

  final FlutterSecureStorage _storage;

  Future<String?> readAccess() => _read(_access);
  Future<String?> readRefresh() => _read(_refresh);

  Future<void> saveTokens({required String access, required String refresh}) async {
    TokenPersist.write(_access, access);
    TokenPersist.write(_refresh, refresh);
    try {
      await _storage.write(key: _access, value: access);
      await _storage.write(key: _refresh, value: refresh);
    } catch (_) {}
  }

  Future<void> clearTokens() async {
    TokenPersist.delete(_access);
    TokenPersist.delete(_refresh);
    try {
      await _storage.delete(key: _access);
      await _storage.delete(key: _refresh);
    } catch (_) {}
  }

  Future<String> deviceId() async {
    final existing = await _read(_device);
    if (existing != null && existing.isNotEmpty) {
      return existing;
    }
    final created = const Uuid().v4();
    TokenPersist.write(_device, created);
    try {
      await _storage.write(key: _device, value: created);
    } catch (_) {}
    return created;
  }

  Future<String?> _read(String key) async {
    final cached = TokenPersist.read(key);
    if (cached != null && cached.isNotEmpty) {
      return cached;
    }
    try {
      final value = await _storage.read(key: key);
      if (value != null && value.isNotEmpty) {
        TokenPersist.write(key, value);
        return value;
      }
    } catch (_) {}
    return null;
  }
}

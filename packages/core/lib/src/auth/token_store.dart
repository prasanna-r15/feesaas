import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:uuid/uuid.dart';

class TokenStore {
  TokenStore({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  static const _access = 'feesaas.access';
  static const _refresh = 'feesaas.refresh';
  static const _device = 'feesaas.device';

  final FlutterSecureStorage _storage;

  Future<String?> readAccess() => _storage.read(key: _access);
  Future<String?> readRefresh() => _storage.read(key: _refresh);

  Future<void> saveTokens({required String access, required String refresh}) async {
    await _storage.write(key: _access, value: access);
    await _storage.write(key: _refresh, value: refresh);
  }

  Future<void> clearTokens() async {
    await _storage.delete(key: _access);
    await _storage.delete(key: _refresh);
  }

  Future<String> deviceId() async {
    try {
      final existing = await _storage.read(key: _device);
      if (existing != null && existing.isNotEmpty) {
        return existing;
      }
      final created = const Uuid().v4();
      await _storage.write(key: _device, value: created);
      return created;
    } catch (_) {
      return const Uuid().v4();
    }
  }
}

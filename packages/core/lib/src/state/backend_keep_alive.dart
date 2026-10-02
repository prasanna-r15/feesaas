import 'dart:async';

import 'package:dio/dio.dart';
import 'package:feesaas_core/src/network/dio_factory.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Pings the public keep-alive URL so a sleeping Render instance can start
/// warming as soon as the app is opened (including the login screen).
final backendKeepAliveProvider = Provider<void>((ref) {
  final dio = Dio(BaseOptions(
    baseUrl: ref.watch(apiBaseUrlProvider),
    connectTimeout: const Duration(seconds: 60),
    receiveTimeout: const Duration(seconds: 60),
    responseType: ResponseType.json,
  ));
  Timer? timer;
  var generation = 0;

  Future<void> cycle() async {
    final gen = ++generation;
    try {
      await dio.get<Map<String, dynamic>>('/api/health/keep-alive');
    } catch (_) {}
    var enabled = true;
    var minutes = 10;
    try {
      final response = await dio.get<Map<String, dynamic>>('/api/health/keep-alive/settings');
      final data = response.data ?? const {};
      enabled = data['enabled'] != false;
      final parsed = (data['intervalMinutes'] as num?)?.toInt();
      if (parsed != null && parsed >= 1 && parsed <= 1440) {
        minutes = parsed;
      }
    } catch (_) {}
    if (gen != generation) {
      return;
    }
    timer?.cancel();
    timer = Timer(enabled ? Duration(minutes: minutes) : const Duration(seconds: 60), cycle);
  }

  ref.onDispose(() {
    generation++;
    timer?.cancel();
    dio.close();
  });
  Future<void>.microtask(cycle);
});

import 'package:dio/dio.dart';
import 'package:feesaas_core/src/auth/session_hooks.dart';
import 'package:feesaas_core/src/auth/token_store.dart';
import 'package:feesaas_core/src/network/auth_interceptor.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

final apiBaseUrlProvider = Provider<String>((ref) {
  const fromEnv = String.fromEnvironment('API_BASE_URL');
  if (fromEnv.isNotEmpty) {
    return fromEnv;
  }
  return 'http://127.0.0.1:8085';
});

final tokenStoreProvider = Provider<TokenStore>((ref) => TokenStore());

final sessionHooksProvider = Provider<SessionHooks>((ref) => SessionHooks());

final sessionInvalidationProvider = Provider<void Function()>((ref) {
  return () => ref.read(sessionHooksProvider).onInvalid?.call();
});

final dioProvider = Provider<Dio>((ref) {
  final baseUrl = ref.watch(apiBaseUrlProvider);
  final store = ref.watch(tokenStoreProvider);
  final refreshDio = Dio(BaseOptions(
    baseUrl: baseUrl,
    connectTimeout: const Duration(seconds: 15),
    receiveTimeout: const Duration(seconds: 20),
    contentType: Headers.jsonContentType,
    responseType: ResponseType.json,
    headers: {'X-Request-Id': const Uuid().v4()},
  ));
  final dio = Dio(BaseOptions(
    baseUrl: baseUrl,
    connectTimeout: const Duration(seconds: 15),
    receiveTimeout: const Duration(seconds: 20),
    contentType: Headers.jsonContentType,
    responseType: ResponseType.json,
  ));
  dio.interceptors.add(InterceptorsWrapper(onRequest: (options, handler) {
    options.headers['X-Request-Id'] = const Uuid().v4();
    handler.next(options);
  }));
  dio.interceptors.add(AuthInterceptor(
    store: store,
    refreshDio: refreshDio,
    onSessionInvalid: () => ref.read(sessionInvalidationProvider)(),
  ));
  return dio;
});

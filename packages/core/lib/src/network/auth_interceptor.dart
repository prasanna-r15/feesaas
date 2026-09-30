import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:feesaas_api_client/feesaas_api_client.dart';
import 'package:feesaas_core/src/auth/token_store.dart';

class AuthInterceptor extends Interceptor {
  AuthInterceptor({
    required TokenStore store,
    required Dio refreshDio,
    required void Function() onSessionInvalid,
  })  : _store = store,
        _refreshDio = refreshDio,
        _onSessionInvalid = onSessionInvalid;

  final TokenStore _store;
  final Dio _refreshDio;
  final void Function() _onSessionInvalid;

  Completer<void>? _refreshing;

  static const _skipAuth = {
    '/api/v1/auth/register',
    '/api/v1/auth/register/verify',
    '/api/v1/auth/login',
    '/api/v1/auth/refresh',
    '/api/v1/auth/logout',
    '/api/v1/auth/password/forgot',
    '/api/v1/auth/password/reset',
  };

  @override
  Future<void> onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
    final path = options.uri.path;
    if (!_skipAuth.contains(path)) {
      final access = await _store.readAccess();
      if (access != null) {
        options.headers['Authorization'] = 'Bearer $access';
      }
    }
    handler.next(options);
  }

  @override
  Future<void> onError(DioException err, ErrorInterceptorHandler handler) async {
    final status = err.response?.statusCode;
    final path = err.requestOptions.uri.path;
    if (status != 401 || _skipAuth.contains(path) || err.requestOptions.extra['retried'] == true) {
      handler.next(_map(err));
      return;
    }

    try {
      await _refreshOnce();
      final access = await _store.readAccess();
      if (access == null) {
        _onSessionInvalid();
        handler.next(_map(err));
        return;
      }
      final request = err.requestOptions;
      request.headers['Authorization'] = 'Bearer $access';
      request.extra['retried'] = true;
      final clone = await _refreshDio.fetch(request);
      handler.resolve(clone);
    } catch (_) {
      await _store.clearTokens();
      _onSessionInvalid();
      handler.next(_map(err));
    }
  }

  Future<void> _refreshOnce() async {
    if (_refreshing != null) {
      return _refreshing!.future;
    }
    final gate = Completer<void>();
    _refreshing = gate;
    try {
      final refresh = await _store.readRefresh();
      if (refresh == null) {
        throw StateError('No refresh token');
      }
      final deviceId = await _store.deviceId();
      final response = await _refreshDio.post<Map<String, dynamic>>(
        '/api/v1/auth/refresh',
        data: jsonEncode({'refreshToken': refresh, 'deviceId': deviceId}),
        options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
      );
      final tokens = TokenResponse.fromJson(response.data!);
      await _store.saveTokens(access: tokens.accessToken, refresh: tokens.refreshToken);
      gate.complete();
    } catch (e, st) {
      gate.completeError(e, st);
      rethrow;
    } finally {
      _refreshing = null;
    }
  }

  DioException _map(DioException err) {
    final data = err.response?.data;
    if (data is Map<String, dynamic>) {
      return DioException(
        requestOptions: err.requestOptions,
        response: err.response,
        type: err.type,
        error: ApiProblem.fromJson(err.response?.statusCode ?? 0, data),
      );
    }
    return err;
  }
}

ApiProblem problemOf(Object error) {
  if (error is ApiProblem) {
    return error;
  }
  if (error is DioException) {
    final wrapped = error.error;
    if (wrapped is ApiProblem) {
      return wrapped;
    }
    final data = error.response?.data;
    if (data is Map<String, dynamic>) {
      return ApiProblem.fromJson(error.response?.statusCode ?? 0, data);
    }
    return ApiProblem(
      status: error.response?.statusCode ?? 0,
      code: 'NETWORK',
      detail: 'Could not reach the server. Check your connection.',
    );
  }
  return ApiProblem(status: 0, code: 'INTERNAL_ERROR', detail: error.toString());
}

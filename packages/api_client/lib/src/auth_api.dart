import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:feesaas_api_client/src/models.dart';

class AuthApi {
  AuthApi(this._dio);

  final Dio _dio;

  Future<TokenResponse> login({
    required String identifier,
    required String password,
    required String deviceId,
  }) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/auth/login',
      data: jsonEncode({
        'identifier': identifier,
        'password': password,
        'deviceId': deviceId,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return TokenResponse.fromJson(response.data!);
  }

  Future<RegisterChallenge> startRegister({
    required String fullName,
    required String email,
    required String phone,
    required String password,
  }) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/auth/register',
      data: jsonEncode({
        'fullName': fullName,
        'email': email,
        'phone': phone,
        'password': password,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return RegisterChallenge.fromJson(response.data!);
  }

  Future<TokenResponse> verifyRegister({
    required String challengeId,
    required String otp,
    required String deviceId,
  }) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/auth/register/verify',
      data: jsonEncode({
        'challengeId': challengeId,
        'otp': otp,
        'deviceId': deviceId,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return TokenResponse.fromJson(response.data!);
  }

  Future<TokenResponse> switchContext({
    required String kind,
    String? tenantId,
    String? workspaceId,
    String? groupId,
    required String deviceId,
  }) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/me/context',
      data: jsonEncode({
        'kind': kind,
        if (tenantId != null) 'tenantId': tenantId,
        if (workspaceId != null) 'workspaceId': workspaceId,
        if (groupId != null) 'groupId': groupId,
        'deviceId': deviceId,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return TokenResponse.fromJson(response.data!);
  }

  Future<void> completeOnboarding() async {
    await _dio.post<void>('/api/v1/me/onboarding/complete');
  }

  Future<TokenResponse> refresh({
    required String refreshToken,
    required String deviceId,
  }) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/auth/refresh',
      data: jsonEncode({
        'refreshToken': refreshToken,
        'deviceId': deviceId,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return TokenResponse.fromJson(response.data!);
  }

  Future<void> logout(String refreshToken) async {
    await _dio.post<void>(
      '/api/v1/auth/logout',
      data: jsonEncode({'refreshToken': refreshToken}),
      options: Options(contentType: Headers.jsonContentType),
    );
  }

  Future<BootstrapResponse> bootstrap() async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/me/bootstrap');
    return BootstrapResponse.fromJson(response.data!);
  }
}

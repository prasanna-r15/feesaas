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
    await _dio.post<String>(
      '/api/v1/me/onboarding/complete',
      options: Options(responseType: ResponseType.plain),
    );
  }

  Future<({String id, bool emailed})> submitBusinessEnquiry({
    required String businessName,
    String? city,
    String? message,
  }) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/me/business-enquiries',
      data: jsonEncode({
        'businessName': businessName,
        if (city != null && city.isNotEmpty) 'city': city,
        if (message != null && message.isNotEmpty) 'message': message,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    final data = response.data ?? const {};
    return (
      id: data['id'] as String? ?? '',
      emailed: data['emailed'] as bool? ?? false,
    );
  }

  Future<List<JoinEnquiry>> listMyEnquiries() async {
    final response = await _dio.get<List<dynamic>>('/api/v1/me/business-enquiries');
    return (response.data ?? const []).map((e) => JoinEnquiry.fromJson(e as Map<String, dynamic>)).toList();
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
    await _dio.post<String>(
      '/api/v1/auth/logout',
      data: jsonEncode({'refreshToken': refreshToken}),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.plain),
    );
  }

  Future<BootstrapResponse> bootstrap() async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/me/bootstrap');
    return BootstrapResponse.fromJson(response.data!);
  }

  Future<({bool accepted, String? challengeId})> forgotPassword(String identifier) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/auth/password/forgot',
      data: jsonEncode({'identifier': identifier}),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    final data = response.data ?? const {};
    return (
      accepted: data['accepted'] as bool? ?? true,
      challengeId: data['challengeId'] as String?,
    );
  }

  Future<void> resetPassword({
    required String challengeId,
    required String otp,
    required String newPassword,
  }) async {
    await _dio.post<String>(
      '/api/v1/auth/password/reset',
      data: jsonEncode({
        'challengeId': challengeId,
        'resetToken': otp,
        'newPassword': newPassword,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.plain),
    );
  }

  Future<List<SupportMessage>> listMySupport() async {
    final response = await _dio.get<List<dynamic>>('/api/v1/me/support/messages');
    return (response.data ?? const []).map((e) => SupportMessage.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<SupportMessage> sendMySupport(String body) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/me/support/messages',
      data: jsonEncode({'body': body}),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return SupportMessage.fromJson(response.data ?? const {});
  }

  Future<int> mySupportUnread() async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/me/support/unread');
    return (response.data?['unreadCount'] as num?)?.toInt() ?? 0;
  }
}

import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:feesaas_api_client/src/models.dart';

class SettingsApi {
  SettingsApi(this._dio);

  final Dio _dio;

  Future<TenantContact> getContact() async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/settings/contact');
    return TenantContact.fromJson(response.data!);
  }

  Future<TenantContact> saveContact({String? phone, String? whatsappNumber}) async {
    final response = await _dio.put<Map<String, dynamic>>(
      '/api/v1/settings/contact',
      data: jsonEncode({
        'phone': phone,
        'whatsappNumber': whatsappNumber,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return TenantContact.fromJson(response.data!);
  }
}

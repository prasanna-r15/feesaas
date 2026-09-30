import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:feesaas_api_client/src/models.dart';

class StaffApi {
  StaffApi(this._dio);

  final Dio _dio;

  Future<List<StaffMember>> list() async {
    final response = await _dio.get<List<dynamic>>('/api/v1/staff');
    return (response.data ?? const [])
        .map((e) => StaffMember.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<StaffMember> create({
    required String fullName,
    String? email,
    String? phone,
    required String password,
    List<String>? permissions,
  }) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/staff',
      data: jsonEncode({
        'fullName': fullName,
        if (email != null && email.isNotEmpty) 'email': email,
        if (phone != null && phone.isNotEmpty) 'phone': phone,
        'password': password,
        if (permissions != null) 'permissions': permissions,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return StaffMember.fromJson(response.data!);
  }

  Future<void> delete(String id) async {
    await _dio.delete<void>('/api/v1/staff/$id');
  }

  Future<List<PermissionDef>> catalogue() async {
    final response = await _dio.get<List<dynamic>>('/api/v1/roles');
    return (response.data ?? const [])
        .map((e) => PermissionDef.fromJson(e as Map<String, dynamic>))
        .toList();
  }
}

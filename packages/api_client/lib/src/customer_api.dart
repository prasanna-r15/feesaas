import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:feesaas_api_client/src/models.dart';

class CustomerApi {
  CustomerApi(this._dio);

  final Dio _dio;

  Future<List<Customer>> list({String? q, String? status, String? branchId}) async {
    final response = await _dio.get<List<dynamic>>(
      '/api/v1/customers',
      queryParameters: {
        if (q != null && q.isNotEmpty) 'q': q,
        if (status != null && status.isNotEmpty) 'status': status,
        if (branchId != null && branchId.isNotEmpty) 'branchId': branchId,
      },
    );
    return (response.data ?? const []).map((e) => Customer.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<List<int>> exportXlsx() async {
    final response = await _dio.get<List<int>>(
      '/api/v1/customers/export',
      options: Options(responseType: ResponseType.bytes),
    );
    return response.data ?? const [];
  }

  Future<Customer> get(String id) async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/customers/$id');
    return Customer.fromJson(response.data!);
  }

  Future<Customer> create({
    required String fullName,
    String? phone,
    String? email,
    String? notes,
    required String dueDate,
    String? feePlanId,
    bool hasWhatsapp = true,
    String? branchId,
  }) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/customers',
      data: jsonEncode({
        'fullName': fullName,
        if (phone != null && phone.isNotEmpty) 'phone': phone,
        if (email != null && email.isNotEmpty) 'email': email,
        if (notes != null && notes.isNotEmpty) 'notes': notes,
        'dueDate': dueDate,
        if (feePlanId != null) 'feePlanId': feePlanId,
        'hasWhatsapp': hasWhatsapp,
        if (branchId != null) 'branchId': branchId,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return Customer.fromJson(response.data!);
  }

  Future<Customer> patch(
    String id, {
    String? fullName,
    String? phone,
    String? email,
    String? status,
    String? notes,
    String? dueDate,
    String? feePlanId,
    bool? hasWhatsapp,
    String? branchId,
  }) async {
    final response = await _dio.patch<Map<String, dynamic>>(
      '/api/v1/customers/$id',
      data: jsonEncode({
        if (fullName != null) 'fullName': fullName,
        if (phone != null) 'phone': phone,
        if (email != null) 'email': email,
        if (status != null) 'status': status,
        if (notes != null) 'notes': notes,
        if (dueDate != null) 'dueDate': dueDate,
        if (feePlanId != null) 'feePlanId': feePlanId,
        if (hasWhatsapp != null) 'hasWhatsapp': hasWhatsapp,
        if (branchId != null) 'branchId': branchId,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return Customer.fromJson(response.data!);
  }

  Future<void> delete(String id) async {
    await _dio.delete<void>('/api/v1/customers/$id');
  }

  Future<List<int>> downloadTemplate() async {
    final response = await _dio.get<List<int>>(
      '/api/v1/imports/customers/template',
      options: Options(responseType: ResponseType.bytes),
    );
    return response.data ?? const [];
  }

  Future<ImportResult> importCsv(List<int> bytes, {String filename = 'members.csv'}) async {
    final form = FormData.fromMap({
      'file': MultipartFile.fromBytes(bytes, filename: filename),
    });
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/imports/customers',
      data: form,
      options: Options(contentType: 'multipart/form-data', responseType: ResponseType.json),
    );
    return ImportResult.fromJson(response.data!);
  }
}

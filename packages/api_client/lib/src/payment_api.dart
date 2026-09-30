import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:feesaas_api_client/src/models.dart';

class PaymentApi {
  PaymentApi(this._dio);

  final Dio _dio;

  Future<List<PaymentRecord>> list() async {
    final response = await _dio.get<List<dynamic>>('/api/v1/payments');
    return (response.data ?? const []).map((e) => PaymentRecord.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<PaymentRecord> get(String id) async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/payments/$id');
    return PaymentRecord.fromJson(response.data!);
  }

  Future<PaymentRecord> voidPayment(String id, {String? reason}) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/payments/$id/void',
      data: jsonEncode({if (reason != null && reason.isNotEmpty) 'reason': reason}),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return PaymentRecord.fromJson(response.data!);
  }
}

import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:feesaas_api_client/src/models.dart';

class FeeApi {
  FeeApi(this._dio);

  final Dio _dio;

  Future<List<PendingFee>> pending({String? bucket, String? branchId}) async {
    final response = await _dio.get<List<dynamic>>(
      '/api/v1/fees/pending',
      queryParameters: {
        if (bucket != null && bucket.isNotEmpty) 'bucket': bucket,
        if (branchId != null && branchId.isNotEmpty) 'branchId': branchId,
      },
    );
    return (response.data ?? const []).map((e) => PendingFee.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<PendingSummary> summary() async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/fees/pending/summary');
    return PendingSummary.fromJson(response.data!);
  }

  Future<PendingFee> collect(
    String feeId, {
    int? amountMinor,
    String method = 'CASH',
    String? referenceNo,
  }) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/fees/$feeId/collect',
      data: jsonEncode({
        if (amountMinor != null) 'amountMinor': amountMinor,
        'method': method,
        if (referenceNo != null && referenceNo.isNotEmpty) 'referenceNo': referenceNo,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return PendingFee.fromJson(response.data!);
  }

  Future<RemindPayload> remind(String feeId) async {
    final response = await _dio.post<Map<String, dynamic>>('/api/v1/fees/$feeId/remind');
    return RemindPayload.fromJson(response.data!);
  }

  Future<List<FeePlan>> listPlans() async {
    final response = await _dio.get<List<dynamic>>('/api/v1/fee-plans');
    return (response.data ?? const []).map((e) => FeePlan.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<FeePlan> getPlan(String id) async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/fee-plans/$id');
    return FeePlan.fromJson(response.data!);
  }

  Future<FeePlan> createPlan({
    required String name,
    required int amountMinor,
    String billingCycle = 'MONTHLY',
    int graceDays = 0,
    bool isDefault = false,
  }) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/fee-plans',
      data: jsonEncode({
        'name': name,
        'amountMinor': amountMinor,
        'billingCycle': billingCycle,
        'graceDays': graceDays,
        'isDefault': isDefault,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return FeePlan.fromJson(response.data!);
  }

  Future<FeePlan> patchPlan(
    String id, {
    String? name,
    int? amountMinor,
    String? billingCycle,
    int? graceDays,
    bool? isDefault,
  }) async {
    final response = await _dio.patch<Map<String, dynamic>>(
      '/api/v1/fee-plans/$id',
      data: jsonEncode({
        if (name != null) 'name': name,
        if (amountMinor != null) 'amountMinor': amountMinor,
        if (billingCycle != null) 'billingCycle': billingCycle,
        if (graceDays != null) 'graceDays': graceDays,
        if (isDefault != null) 'isDefault': isDefault,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return FeePlan.fromJson(response.data!);
  }

  Future<void> deletePlan(String id) async {
    await _dio.delete<void>('/api/v1/fee-plans/$id');
  }
}

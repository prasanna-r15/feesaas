import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:feesaas_api_client/src/models.dart';

class AttendanceApi {
  AttendanceApi(this._dio);

  final Dio _dio;

  Future<List<Batch>> listBatches() async {
    final response = await _dio.get<List<dynamic>>('/api/v1/batches');
    return (response.data ?? const []).map((e) => Batch.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<Batch> createBatch({required String name, String? schedule}) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/batches',
      data: jsonEncode({'name': name, if (schedule != null && schedule.isNotEmpty) 'schedule': schedule}),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return Batch.fromJson(response.data!);
  }

  Future<void> deleteBatch(String id) async {
    await _dio.delete<void>('/api/v1/batches/$id');
  }

  Future<List<AttendanceMember>> members(String batchId) async {
    final response = await _dio.get<List<dynamic>>('/api/v1/batches/$batchId/members');
    return (response.data ?? const []).map((e) => AttendanceMember.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> addMember(String batchId, String customerId) async {
    await _dio.post<void>(
      '/api/v1/batches/$batchId/members',
      data: jsonEncode({'customerId': customerId}),
      options: Options(contentType: Headers.jsonContentType),
    );
  }

  Future<void> removeMember(String batchId, String customerId) async {
    await _dio.delete<void>('/api/v1/batches/$batchId/members/$customerId');
  }

  Future<AttendanceRoster> roster({required String batchId, String? on}) async {
    final response = await _dio.get<Map<String, dynamic>>(
      '/api/v1/attendance',
      queryParameters: {
        'batchId': batchId,
        if (on != null && on.isNotEmpty) 'on': on,
      },
    );
    return AttendanceRoster.fromJson(response.data!);
  }

  Future<AttendanceRoster> mark({
    required String batchId,
    required String customerId,
    required String status,
    String? markedOn,
  }) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/attendance',
      data: jsonEncode({
        'batchId': batchId,
        'customerId': customerId,
        'status': status,
        if (markedOn != null) 'markedOn': markedOn,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return AttendanceRoster.fromJson(response.data!);
  }
}

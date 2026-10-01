import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:feesaas_api_client/src/models.dart';

class PersonalApi {
  PersonalApi(this._dio);

  final Dio _dio;

  Future<List<PersonalCategory>> categories() async {
    final response = await _dio.get<List<dynamic>>('/api/v1/personal/categories');
    return (response.data ?? const []).map((e) => PersonalCategory.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<List<PersonalExpense>> expenses({String? month}) async {
    final response = await _dio.get<List<dynamic>>(
      '/api/v1/personal/expenses',
      queryParameters: {if (month != null) 'month': month},
    );
    return (response.data ?? const []).map((e) => PersonalExpense.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> addExpense({
    required int amountMinor,
    required String categoryId,
    String? description,
    String? occurredOn,
    String? method,
  }) async {
    await _dio.post<Map<String, dynamic>>(
      '/api/v1/personal/expenses',
      data: jsonEncode({
        'amountMinor': amountMinor,
        'categoryId': categoryId,
        if (description != null) 'description': description,
        if (occurredOn != null) 'occurredOn': occurredOn,
        if (method != null) 'method': method,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
  }

  Future<void> updateExpense(
    String id, {
    required int amountMinor,
    required String categoryId,
    String? description,
    String? occurredOn,
    String? method,
  }) async {
    await _dio.patch<Map<String, dynamic>>(
      '/api/v1/personal/expenses/$id',
      data: jsonEncode({
        'amountMinor': amountMinor,
        'categoryId': categoryId,
        if (description != null) 'description': description,
        if (occurredOn != null) 'occurredOn': occurredOn,
        if (method != null) 'method': method,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
  }

  Future<void> deleteExpense(String id) async {
    await _dio.delete<String>(
      '/api/v1/personal/expenses/$id',
      options: Options(responseType: ResponseType.plain),
    );
  }

  Future<List<PersonalIncome>> income({String? month}) async {
    final response = await _dio.get<List<dynamic>>(
      '/api/v1/personal/income',
      queryParameters: {if (month != null) 'month': month},
    );
    return (response.data ?? const []).map((e) => PersonalIncome.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> addIncome({
    required int amountMinor,
    required String source,
    String? description,
    String? occurredOn,
  }) async {
    await _dio.post<Map<String, dynamic>>(
      '/api/v1/personal/income',
      data: jsonEncode({
        'amountMinor': amountMinor,
        'source': source,
        if (description != null) 'description': description,
        if (occurredOn != null) 'occurredOn': occurredOn,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
  }

  Future<void> updateIncome(
    String id, {
    required int amountMinor,
    required String source,
    String? description,
    String? occurredOn,
  }) async {
    await _dio.patch<Map<String, dynamic>>(
      '/api/v1/personal/income/$id',
      data: jsonEncode({
        'amountMinor': amountMinor,
        'source': source,
        if (description != null) 'description': description,
        if (occurredOn != null) 'occurredOn': occurredOn,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
  }

  Future<void> deleteIncome(String id) async {
    await _dio.delete<String>(
      '/api/v1/personal/income/$id',
      options: Options(responseType: ResponseType.plain),
    );
  }

  Future<List<PersonalBudget>> budgets({String? month}) async {
    final response = await _dio.get<List<dynamic>>(
      '/api/v1/personal/budgets',
      queryParameters: {if (month != null) 'month': month},
    );
    return (response.data ?? const []).map((e) => PersonalBudget.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> saveBudget({required String categoryId, required int limitMinor, String? yearMonth}) async {
    await _dio.put<List<dynamic>>(
      '/api/v1/personal/budgets',
      data: jsonEncode({
        'categoryId': categoryId,
        'limitMinor': limitMinor,
        if (yearMonth != null) 'yearMonth': yearMonth,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
  }

  Future<void> updateBudget(String id, {required String categoryId, required int limitMinor}) async {
    await _dio.put<List<dynamic>>(
      '/api/v1/personal/budgets/$id',
      data: jsonEncode({'categoryId': categoryId, 'limitMinor': limitMinor}),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
  }

  Future<void> deleteBudget(String id) async {
    await _dio.delete<String>(
      '/api/v1/personal/budgets/$id',
      options: Options(responseType: ResponseType.plain),
    );
  }

  Future<PersonalSummary> summary({String? month}) async {
    final response = await _dio.get<Map<String, dynamic>>(
      '/api/v1/personal/summary',
      queryParameters: {if (month != null) 'month': month},
    );
    return PersonalSummary.fromJson(response.data!);
  }
}

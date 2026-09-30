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
    final response = await _dio.get<List<dynamic>>('/api/v1/personal/expenses', queryParameters: {if (month != null) 'month': month});
    return (response.data ?? const []).map((e) => PersonalExpense.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> addExpense({
    required int amountMinor,
    required String categoryId,
    String? description,
    String? occurredOn,
    String? method,
  }) async {
    await _dio.post<void>(
      '/api/v1/personal/expenses',
      data: jsonEncode({
        'amountMinor': amountMinor,
        'categoryId': categoryId,
        if (description != null) 'description': description,
        if (occurredOn != null) 'occurredOn': occurredOn,
        if (method != null) 'method': method,
      }),
      options: Options(contentType: Headers.jsonContentType),
    );
  }

  Future<void> addIncome({required int amountMinor, required String source, String? description}) async {
    await _dio.post<void>(
      '/api/v1/personal/income',
      data: jsonEncode({'amountMinor': amountMinor, 'source': source, if (description != null) 'description': description}),
      options: Options(contentType: Headers.jsonContentType),
    );
  }

  Future<List<PersonalBudget>> budgets({String? month}) async {
    final response = await _dio.get<List<dynamic>>('/api/v1/personal/budgets', queryParameters: {if (month != null) 'month': month});
    return (response.data ?? const []).map((e) => PersonalBudget.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> saveBudget({required String categoryId, required int limitMinor}) async {
    await _dio.put<void>(
      '/api/v1/personal/budgets',
      data: jsonEncode({'categoryId': categoryId, 'limitMinor': limitMinor}),
      options: Options(contentType: Headers.jsonContentType),
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

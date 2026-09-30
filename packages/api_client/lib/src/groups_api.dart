import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:feesaas_api_client/src/models.dart';

class GroupsApi {
  GroupsApi(this._dio);

  final Dio _dio;

  Future<List<ExpenseGroup>> list() async {
    final response = await _dio.get<List<dynamic>>('/api/v1/groups');
    return (response.data ?? const []).map((e) => ExpenseGroup.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<ExpenseGroup> get(String groupId) async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/groups/$groupId');
    return ExpenseGroup.fromJson(response.data!);
  }

  Future<ExpenseGroup> create({required String name, String type = 'CUSTOM'}) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/groups',
      data: jsonEncode({'name': name, 'type': type}),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return ExpenseGroup.fromJson(response.data!);
  }

  Future<List<GroupMember>> members(String groupId) async {
    final response = await _dio.get<List<dynamic>>('/api/v1/groups/$groupId/members');
    return (response.data ?? const []).map((e) => GroupMember.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> addMember(String groupId, {required String displayName}) async {
    await _dio.post<void>(
      '/api/v1/groups/$groupId/members',
      data: jsonEncode({'displayName': displayName}),
      options: Options(contentType: Headers.jsonContentType),
    );
  }

  Future<void> addExpense(
    String groupId, {
    required String description,
    required int amountMinor,
    required String paidBy,
    required String splitMethod,
    required List<Map<String, dynamic>> shares,
  }) async {
    await _dio.post<void>(
      '/api/v1/groups/$groupId/expenses',
      data: jsonEncode({
        'description': description,
        'amountMinor': amountMinor,
        'paidBy': paidBy,
        'splitMethod': splitMethod,
        'shares': shares,
      }),
      options: Options(contentType: Headers.jsonContentType),
    );
  }

  Future<void> updateExpense(
    String groupId,
    String expenseId, {
    required String description,
    required int amountMinor,
    required String paidBy,
    required String splitMethod,
    required List<Map<String, dynamic>> shares,
  }) async {
    await _dio.put<void>(
      '/api/v1/groups/$groupId/expenses/$expenseId',
      data: jsonEncode({
        'description': description,
        'amountMinor': amountMinor,
        'paidBy': paidBy,
        'splitMethod': splitMethod,
        'shares': shares,
      }),
      options: Options(contentType: Headers.jsonContentType),
    );
  }

  Future<void> markSharePaid(String groupId, String expenseId, String memberId) async {
    await _dio.post<void>('/api/v1/groups/$groupId/expenses/$expenseId/shares/$memberId/settle');
  }

  Future<List<GroupExpenseItem>> expenses(String groupId) async {
    final response = await _dio.get<List<dynamic>>('/api/v1/groups/$groupId/expenses');
    return (response.data ?? const []).map((e) => GroupExpenseItem.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<GroupBalances> balances(String groupId) async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/groups/$groupId/balances');
    return GroupBalances.fromJson(response.data!);
  }

  Future<GroupInviteTicket> invite(String groupId) async {
    final response = await _dio.post<Map<String, dynamic>>('/api/v1/groups/$groupId/invitations');
    return GroupInviteTicket.fromJson(response.data!);
  }

  Future<GroupInvitePreview> previewInvite(String token) async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/public/group-invites/${Uri.encodeComponent(token)}');
    return GroupInvitePreview.fromJson(response.data!);
  }

  Future<void> acceptInvite(String token) async {
    await _dio.post<void>('/api/v1/group-invitations/${Uri.encodeComponent(token)}');
  }
}

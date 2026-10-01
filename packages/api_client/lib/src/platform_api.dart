import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:feesaas_api_client/src/models.dart';

class PlatformApi {
  PlatformApi(this._dio);

  final Dio _dio;

  Future<List<TenantSummary>> listTenants({
    String? q,
    String? status,
    String? plan,
    String? businessType,
  }) async {
    final response = await _dio.get<List<dynamic>>(
      '/api/v1/platform/tenants',
      queryParameters: {
        if (q != null && q.isNotEmpty) 'q': q,
        if (status != null && status.isNotEmpty) 'status': status,
        if (plan != null && plan.isNotEmpty) 'plan': plan,
        if (businessType != null && businessType.isNotEmpty) 'businessType': businessType,
      },
    );
    return (response.data ?? const [])
        .map((e) => TenantSummary.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<TenantSummary> getTenant(String id) async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/platform/tenants/$id');
    return TenantSummary.fromJson(response.data!);
  }

  Future<TenantSummary> createTenant({
    required String name,
    required String slug,
    required String businessType,
    required String ownerFullName,
    required String ownerEmail,
    required String ownerPassword,
    String? logoBase64,
  }) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/platform/tenants',
      data: jsonEncode({
        'name': name,
        'slug': slug,
        'businessType': businessType,
        'owner': {
          'fullName': ownerFullName,
          'email': ownerEmail,
          'password': ownerPassword,
        },
        if (logoBase64 != null && logoBase64.isNotEmpty) 'logoBase64': logoBase64,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return TenantSummary.fromJson(response.data!);
  }

  Future<TenantSummary> patchTenant(
    String id, {
    String? name,
    String? slug,
    String? timezone,
    String? currency,
    String? logoBase64,
    bool clearLogo = false,
    String? displayName,
    String? accentColor,
    String? trialEndsAt,
    int? graceDays,
    String? billingStatus,
    int? customMaxMembers,
    int? customMaxStaff,
    bool clearCustomLimits = false,
    String? phone,
    String? whatsappNumber,
  }) async {
    final response = await _dio.patch<Map<String, dynamic>>(
      '/api/v1/platform/tenants/$id',
      data: jsonEncode({
        if (name != null) 'name': name,
        if (slug != null && slug.isNotEmpty) 'slug': slug,
        if (timezone != null) 'timezone': timezone,
        if (currency != null) 'currency': currency,
        if (clearLogo) 'clearLogo': true,
        if (!clearLogo && logoBase64 != null) 'logoBase64': logoBase64,
        if (displayName != null) 'displayName': displayName,
        if (accentColor != null) 'accentColor': accentColor,
        if (trialEndsAt != null) 'trialEndsAt': trialEndsAt,
        if (graceDays != null) 'graceDays': graceDays,
        if (billingStatus != null) 'billingStatus': billingStatus,
        if (customMaxMembers != null) 'customMaxMembers': customMaxMembers,
        if (customMaxStaff != null) 'customMaxStaff': customMaxStaff,
        if (clearCustomLimits) 'clearCustomLimits': true,
        if (phone != null) 'phone': phone,
        if (whatsappNumber != null) 'whatsappNumber': whatsappNumber,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return TenantSummary.fromJson(response.data!);
  }

  Future<TenantSummary> deleteTenant(String id) async {
    final response = await _dio.delete<Map<String, dynamic>>('/api/v1/platform/tenants/$id');
    return TenantSummary.fromJson(response.data!);
  }

  Future<TenantSummary> setStatus(String id, {required bool suspend}) async {
    final path = suspend
        ? '/api/v1/platform/tenants/$id/suspend'
        : '/api/v1/platform/tenants/$id/activate';
    final response = await _dio.post<Map<String, dynamic>>(path);
    return TenantSummary.fromJson(response.data!);
  }

  Future<TenantSummary> restoreTenant(String id) async {
    final response = await _dio.post<Map<String, dynamic>>('/api/v1/platform/tenants/$id/restore');
    return TenantSummary.fromJson(response.data!);
  }

  Future<TenantSummary> setPlan(String id, String planCode) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/platform/tenants/$id/plan',
      data: jsonEncode({'planCode': planCode}),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return TenantSummary.fromJson(response.data!);
  }

  Future<TenantSummary> setModules(String id, List<String> modules) async {
    final response = await _dio.put<Map<String, dynamic>>(
      '/api/v1/platform/tenants/$id/modules',
      data: jsonEncode({'modules': modules}),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return TenantSummary.fromJson(response.data!);
  }

  Future<List<Map<String, dynamic>>> notes(String id) async {
    final response = await _dio.get<List<dynamic>>('/api/v1/platform/tenants/$id/notes');
    return (response.data ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  Future<List<Map<String, dynamic>>> addNote(String id, String body) async {
    final response = await _dio.post<List<dynamic>>(
      '/api/v1/platform/tenants/$id/notes',
      data: jsonEncode({'body': body}),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return (response.data ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  Future<List<Map<String, dynamic>>> invoices(String id) async {
    final response = await _dio.get<List<dynamic>>('/api/v1/platform/tenants/$id/invoices');
    return (response.data ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  Future<List<Map<String, dynamic>>> addInvoice(
    String id, {
    required String periodLabel,
    required int amountMinor,
    String currency = 'INR',
    String? dueOn,
    String? note,
  }) async {
    final response = await _dio.post<List<dynamic>>(
      '/api/v1/platform/tenants/$id/invoices',
      data: jsonEncode({
        'periodLabel': periodLabel,
        'amountMinor': amountMinor,
        'currency': currency,
        if (dueOn != null) 'dueOn': dueOn,
        if (note != null) 'note': note,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return (response.data ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  Future<List<Map<String, dynamic>>> markInvoice(String tenantId, String invoiceId, String status) async {
    final response = await _dio.post<List<dynamic>>(
      '/api/v1/platform/tenants/$tenantId/invoices/$invoiceId',
      data: jsonEncode({'status': status}),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return (response.data ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  Future<List<Map<String, dynamic>>> audit(String id) async {
    final response = await _dio.get<List<dynamic>>('/api/v1/platform/tenants/$id/audit');
    return (response.data ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  Future<void> updateOwner(String id, {String? fullName, String? email, String? phone}) async {
    await _dio.patch<void>(
      '/api/v1/platform/tenants/$id/owner',
      data: jsonEncode({
        if (fullName != null) 'fullName': fullName,
        if (email != null) 'email': email,
        if (phone != null) 'phone': phone,
      }),
      options: Options(contentType: Headers.jsonContentType),
    );
  }

  Future<String> resetOwnerPassword(String id, {String? password}) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/platform/tenants/$id/owner/password',
      data: jsonEncode({if (password != null) 'password': password}),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return response.data!['password'] as String;
  }

  Future<TokenResponse> impersonate(String id) async {
    final response = await _dio.post<Map<String, dynamic>>('/api/v1/platform/tenants/$id/impersonate');
    return TokenResponse.fromJson(response.data!);
  }

  Future<List<Customer>> members(String id, {String? q}) async {
    final response = await _dio.get<dynamic>(
      '/api/v1/platform/tenants/$id/members',
      queryParameters: {if (q != null && q.isNotEmpty) 'q': q},
    );
    final raw = response.data;
    final list = raw is List ? raw : const [];
    return list
        .whereType<Map>()
        .map((e) => Customer.fromJson(Map<String, dynamic>.from(e)))
        .toList();
  }

  Future<PublicBranding> branding(String slug) async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/public/tenants/$slug/branding');
    return PublicBranding.fromJson(response.data!);
  }

  Future<List<Map<String, dynamic>>> tenantBranches(String tenantId) =>
      _maps('/api/v1/platform/tenants/$tenantId/branches');

  Future<List<Map<String, dynamic>>> createTenantBranch(
    String tenantId, {
    required String name,
    String? address,
    bool primary = false,
  }) async {
    final response = await _dio.post<List<dynamic>>(
      '/api/v1/platform/tenants/$tenantId/branches',
      data: jsonEncode({'name': name, if (address != null) 'address': address, 'primary': primary}),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return (response.data ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  Future<List<Map<String, dynamic>>> deleteTenantBranch(String tenantId, String id) async {
    final response = await _dio.delete<List<dynamic>>('/api/v1/platform/tenants/$tenantId/branches/$id');
    return (response.data ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  Future<List<Map<String, dynamic>>> tenantFeePlans(String tenantId) =>
      _maps('/api/v1/platform/tenants/$tenantId/fee-plans');

  Future<List<Map<String, dynamic>>> createTenantFeePlan(
    String tenantId, {
    required String name,
    required int amountMinor,
    String billingCycle = 'MONTHLY',
    int graceDays = 0,
    bool isDefault = false,
  }) async {
    final response = await _dio.post<List<dynamic>>(
      '/api/v1/platform/tenants/$tenantId/fee-plans',
      data: jsonEncode({
        'name': name,
        'amountMinor': amountMinor,
        'billingCycle': billingCycle,
        'graceDays': graceDays,
        'isDefault': isDefault,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return (response.data ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  Future<List<Map<String, dynamic>>> patchTenantFeePlan(
    String tenantId,
    String id, {
    String? name,
    int? amountMinor,
    String? billingCycle,
    int? graceDays,
    bool? isDefault,
  }) async {
    final response = await _dio.patch<List<dynamic>>(
      '/api/v1/platform/tenants/$tenantId/fee-plans/$id',
      data: jsonEncode({
        if (name != null) 'name': name,
        if (amountMinor != null) 'amountMinor': amountMinor,
        if (billingCycle != null) 'billingCycle': billingCycle,
        if (graceDays != null) 'graceDays': graceDays,
        if (isDefault != null) 'isDefault': isDefault,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return (response.data ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  Future<List<Map<String, dynamic>>> deleteTenantFeePlan(String tenantId, String id) async {
    final response = await _dio.delete<List<dynamic>>('/api/v1/platform/tenants/$tenantId/fee-plans/$id');
    return (response.data ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  Future<List<Map<String, dynamic>>> tenantAddons(String tenantId) =>
      _maps('/api/v1/platform/tenants/$tenantId/addons');

  Future<List<Map<String, dynamic>>> createTenantAddon(
    String tenantId, {
    required String name,
    String? description,
    required int amountMinor,
  }) async {
    final response = await _dio.post<List<dynamic>>(
      '/api/v1/platform/tenants/$tenantId/addons',
      data: jsonEncode({'name': name, if (description != null) 'description': description, 'amountMinor': amountMinor}),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return (response.data ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  Future<List<Map<String, dynamic>>> deleteTenantAddon(String tenantId, String id) async {
    final response = await _dio.delete<List<dynamic>>('/api/v1/platform/tenants/$tenantId/addons/$id');
    return (response.data ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  Future<List<Map<String, dynamic>>> tenantDiets(String tenantId) =>
      _maps('/api/v1/platform/tenants/$tenantId/diet-charts');

  Future<List<Map<String, dynamic>>> createTenantDiet(String tenantId, {required String name, required String body}) async {
    final response = await _dio.post<List<dynamic>>(
      '/api/v1/platform/tenants/$tenantId/diet-charts',
      data: jsonEncode({'name': name, 'body': body}),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return (response.data ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  Future<List<Map<String, dynamic>>> patchTenantDiet(
    String tenantId,
    String id, {
    required String name,
    required String body,
  }) async {
    final response = await _dio.patch<List<dynamic>>(
      '/api/v1/platform/tenants/$tenantId/diet-charts/$id',
      data: jsonEncode({'name': name, 'body': body}),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return (response.data ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  Future<List<IndividualAccount>> listIndividuals({String? q}) async {
    final response = await _dio.get<List<dynamic>>(
      '/api/v1/platform/individuals',
      queryParameters: {if (q != null && q.isNotEmpty) 'q': q},
    );
    return (response.data ?? const []).map((e) => IndividualAccount.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<List<Map<String, dynamic>>> deleteTenantDiet(String tenantId, String id) async {
    final response = await _dio.delete<List<dynamic>>('/api/v1/platform/tenants/$tenantId/diet-charts/$id');
    return (response.data ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  Future<DuesMailSettings> mailSettings() async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/platform/mail');
    return DuesMailSettings.fromJson(response.data!);
  }

  Future<DuesMailSettings> saveMailSettings(DuesMailSettings settings, {String? smtpPassword}) async {
    final response = await _dio.put<Map<String, dynamic>>(
      '/api/v1/platform/mail',
      data: jsonEncode({
        'enabled': settings.enabled,
        'cronExpr': settings.cronExpr,
        'timezone': settings.timezone,
        'smtpHost': settings.smtpHost,
        'smtpPort': settings.smtpPort,
        'smtpUsername': settings.smtpUsername,
        'smtpFrom': settings.smtpFrom,
        if (smtpPassword != null && smtpPassword.isNotEmpty) 'smtpPassword': smtpPassword,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return DuesMailSettings.fromJson(response.data!);
  }

  Future<({int sent, int skipped, String detail})> runDuesEmail() async {
    final response = await _dio.post<Map<String, dynamic>>('/api/v1/platform/mail/run');
    final data = response.data!;
    return (
      sent: (data['sent'] as num).toInt(),
      skipped: (data['skipped'] as num).toInt(),
      detail: data['detail'] as String? ?? '',
    );
  }

  Future<List<PlatformConfigRow>> listConfig({String? q, String? paramKey, String? paramSubKey}) async {
    final response = await _dio.get<List<dynamic>>(
      '/api/v1/platform/config',
      queryParameters: {
        if (q != null && q.isNotEmpty) 'q': q,
        if (paramKey != null && paramKey.isNotEmpty) 'paramKey': paramKey,
        if (paramSubKey != null && paramSubKey.isNotEmpty) 'paramSubKey': paramSubKey,
      },
    );
    return (response.data ?? const [])
        .map((e) => PlatformConfigRow.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<PlatformConfigRow> createConfig({
    required String paramKey,
    required String paramSubKey,
    required String paramValue,
    String? description,
  }) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/platform/config',
      data: jsonEncode({
        'paramKey': paramKey,
        'paramSubKey': paramSubKey,
        'paramValue': paramValue,
        if (description != null) 'description': description,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return PlatformConfigRow.fromJson(response.data!);
  }

  Future<PlatformConfigRow> updateConfig(
    String id, {
    required String paramKey,
    required String paramSubKey,
    required String paramValue,
    String? description,
  }) async {
    final response = await _dio.put<Map<String, dynamic>>(
      '/api/v1/platform/config/$id',
      data: jsonEncode({
        'paramKey': paramKey,
        'paramSubKey': paramSubKey,
        'paramValue': paramValue,
        if (description != null) 'description': description,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return PlatformConfigRow.fromJson(response.data!);
  }

  Future<void> deleteConfig(String id) async {
    await _dio.delete<void>('/api/v1/platform/config/$id');
  }

  Future<List<JoinEnquiry>> listJoinEnquiries() async {
    final response = await _dio.get<List<dynamic>>('/api/v1/platform/join-enquiries');
    return (response.data ?? const []).map((e) => JoinEnquiry.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<({String tenantId, String name, String slug})> approveJoinEnquiry(
    String enquiryId, {
    required String name,
    required String slug,
    required String businessType,
  }) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/platform/join-enquiries/$enquiryId/approve',
      data: jsonEncode({
        'name': name,
        'slug': slug,
        'businessType': businessType,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    final data = response.data ?? const {};
    return (
      tenantId: data['tenantId']?.toString() ?? '',
      name: data['name'] as String? ?? name,
      slug: data['slug'] as String? ?? slug,
    );
  }

  Future<List<SupportThread>> listSupportThreads() async {
    final response = await _dio.get<List<dynamic>>('/api/v1/platform/support/threads');
    return (response.data ?? const []).map((e) => SupportThread.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<SupportThread> openSupportThread(String userId) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/platform/support/threads',
      data: jsonEncode({'userId': userId}),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return SupportThread.fromJson(response.data ?? const {});
  }

  Future<List<SupportMessage>> listSupportMessages(String threadId) async {
    final response = await _dio.get<List<dynamic>>('/api/v1/platform/support/threads/$threadId/messages');
    return (response.data ?? const []).map((e) => SupportMessage.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<SupportMessage> sendSupportReply(String threadId, String body) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/platform/support/threads/$threadId/messages',
      data: jsonEncode({'body': body}),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return SupportMessage.fromJson(response.data ?? const {});
  }

  Future<int> supportUnread() async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/platform/support/unread');
    return (response.data?['unreadCount'] as num?)?.toInt() ?? 0;
  }

  Future<List<Map<String, dynamic>>> _maps(String path) async {
    final response = await _dio.get<List<dynamic>>(path);
    return (response.data ?? const []).map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }
}

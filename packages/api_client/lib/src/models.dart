class TokenResponse {
  TokenResponse({
    required this.accessToken,
    required this.refreshToken,
    required this.tokenType,
    required this.expiresIn,
    required this.user,
  });

  final String accessToken;
  final String refreshToken;
  final String tokenType;
  final int expiresIn;
  final UserSummary user;

  factory TokenResponse.fromJson(Map<String, dynamic> json) {
    return TokenResponse(
      accessToken: json['accessToken'] as String,
      refreshToken: json['refreshToken'] as String,
      tokenType: json['tokenType'] as String? ?? 'Bearer',
      expiresIn: (json['expiresIn'] as num).toInt(),
      user: UserSummary.fromJson(json['user'] as Map<String, dynamic>),
    );
  }
}

class RegisterChallenge {
  RegisterChallenge({
    required this.challengeId,
    required this.channel,
    required this.destination,
    this.otp,
  });

  final String challengeId;
  final String channel;
  final String destination;
  final String? otp;

  factory RegisterChallenge.fromJson(Map<String, dynamic> json) {
    return RegisterChallenge(
      challengeId: json['challengeId'].toString(),
      channel: json['channel'] as String? ?? 'EMAIL',
      destination: json['destination'] as String? ?? '',
      otp: json['otp'] as String?,
    );
  }
}

class UserSummary {
  UserSummary({required this.id, required this.fullName, required this.role, this.tenantId});

  final String id;
  final String fullName;
  final String role;
  final String? tenantId;

  factory UserSummary.fromJson(Map<String, dynamic> json) {
    return UserSummary(
      id: json['id'] as String,
      fullName: json['fullName'] as String,
      role: json['role'] as String,
      tenantId: json['tenantId'] as String?,
    );
  }
}

class BootstrapResponse {
  BootstrapResponse({
    required this.user,
    required this.permissions,
    this.tenant,
    required this.dashboard,
    this.contexts = const [],
    this.activeContext,
    this.needsOnboarding = false,
  });

  final UserSummary user;
  final List<String> permissions;
  final TenantBootstrap? tenant;
  final List<DashboardWidget> dashboard;
  final List<UserContext> contexts;
  final UserContext? activeContext;
  final bool needsOnboarding;

  factory BootstrapResponse.fromJson(Map<String, dynamic> json) {
    return BootstrapResponse(
      user: UserSummary.fromJson(json['user'] as Map<String, dynamic>),
      permissions: (json['permissions'] as List<dynamic>? ?? const []).map((e) => e as String).toList(),
      tenant: json['tenant'] == null ? null : TenantBootstrap.fromJson(json['tenant'] as Map<String, dynamic>),
      dashboard: (json['dashboard'] as List<dynamic>? ?? const [])
          .map((e) => DashboardWidget.fromJson(e as Map<String, dynamic>))
          .toList(),
      contexts: (json['contexts'] as List<dynamic>? ?? const [])
          .map((e) => UserContext.fromJson(e as Map<String, dynamic>))
          .toList(),
      activeContext: json['activeContext'] == null
          ? null
          : UserContext.fromJson(json['activeContext'] as Map<String, dynamic>),
      needsOnboarding: json['needsOnboarding'] as bool? ?? false,
    );
  }
}

class UserContext {
  UserContext({
    required this.kind,
    required this.role,
    this.tenantId,
    this.workspaceId,
    this.groupId,
    required this.label,
  });

  final String kind;
  final String role;
  final String? tenantId;
  final String? workspaceId;
  final String? groupId;
  final String label;

  factory UserContext.fromJson(Map<String, dynamic> json) {
    return UserContext(
      kind: json['kind'] as String,
      role: json['role'] as String? ?? '',
      tenantId: json['tenantId'] as String?,
      workspaceId: json['workspaceId'] as String?,
      groupId: json['groupId'] as String?,
      label: json['label'] as String? ?? json['kind'] as String,
    );
  }
}

class TenantBootstrap {
  TenantBootstrap({
    required this.id,
    required this.name,
    required this.slug,
    required this.businessType,
    required this.status,
    required this.timezone,
    required this.currency,
    required this.labels,
    required this.modules,
    this.logoBase64,
    this.displayName,
    this.accentColor,
  });

  final String id;
  final String name;
  final String slug;
  final String businessType;
  final String status;
  final String timezone;
  final String currency;
  final Map<String, String> labels;
  final List<String> modules;
  final String? logoBase64;
  final String? displayName;
  final String? accentColor;

  String get title => (displayName != null && displayName!.isNotEmpty) ? displayName! : name;

  factory TenantBootstrap.fromJson(Map<String, dynamic> json) {
    final labelsRaw = json['labels'] as Map<String, dynamic>? ?? const {};
    return TenantBootstrap(
      id: json['id'] as String,
      name: json['name'] as String,
      slug: json['slug'] as String,
      businessType: json['businessType'] as String,
      status: json['status'] as String,
      timezone: json['timezone'] as String,
      currency: json['currency'] as String,
      labels: labelsRaw.map((k, v) => MapEntry(k, v?.toString() ?? '')),
      modules: (json['modules'] as List<dynamic>? ?? const []).map((e) => e as String).toList(),
      logoBase64: json['logoBase64'] as String?,
      displayName: json['displayName'] as String?,
      accentColor: json['accentColor'] as String?,
    );
  }

  String label(String key, String fallback) => labels[key] ?? fallback;
}

class DashboardWidget {
  DashboardWidget({required this.id, required this.module});

  final String id;
  final String module;

  factory DashboardWidget.fromJson(Map<String, dynamic> json) {
    return DashboardWidget(id: json['id'] as String, module: json['module'] as String);
  }
}

class StaffMember {
  StaffMember({
    required this.id,
    required this.fullName,
    this.email,
    this.phone,
    required this.role,
    required this.status,
    required this.permissions,
  });

  final String id;
  final String fullName;
  final String? email;
  final String? phone;
  final String role;
  final String status;
  final List<String> permissions;

  factory StaffMember.fromJson(Map<String, dynamic> json) {
    return StaffMember(
      id: json['id'] as String,
      fullName: json['fullName'] as String,
      email: json['email'] as String?,
      phone: json['phone'] as String?,
      role: json['role'] as String,
      status: json['status'] as String,
      permissions: (json['permissions'] as List<dynamic>? ?? const [])
          .map((e) => e as String)
          .toList(),
    );
  }
}

class PermissionDef {
  PermissionDef({required this.code, required this.module, required this.description});

  final String code;
  final String module;
  final String description;

  factory PermissionDef.fromJson(Map<String, dynamic> json) {
    return PermissionDef(
      code: json['code'] as String,
      module: json['module'] as String,
      description: json['description'] as String,
    );
  }
}

class TenantSummary {
  TenantSummary({
    required this.id,
    required this.name,
    required this.slug,
    required this.businessType,
    required this.status,
    required this.modules,
    this.ownerId,
    this.planCode,
    this.maxMembers,
    this.maxStaff,
    this.timezone,
    this.currency,
    this.hasLogo = false,
    this.logoBase64,
    this.displayName,
    this.accentColor,
    this.trialEndsAt,
    this.graceDays,
    this.billingStatus,
    this.customMaxMembers,
    this.customMaxStaff,
    this.phone,
    this.whatsappNumber,
    this.memberCount,
    this.staffCount,
    this.lastLoginAt,
    this.lastCollectionOn,
    this.ownerFullName,
    this.ownerEmail,
    this.ownerPhone,
  });

  final String id;
  final String name;
  final String slug;
  final String businessType;
  final String status;
  final List<String> modules;
  final String? ownerId;
  final String? planCode;
  final int? maxMembers;
  final int? maxStaff;
  final String? timezone;
  final String? currency;
  final bool hasLogo;
  final String? logoBase64;
  final String? displayName;
  final String? accentColor;
  final String? trialEndsAt;
  final int? graceDays;
  final String? billingStatus;
  final int? customMaxMembers;
  final int? customMaxStaff;
  final String? phone;
  final String? whatsappNumber;
  final int? memberCount;
  final int? staffCount;
  final String? lastLoginAt;
  final String? lastCollectionOn;
  final String? ownerFullName;
  final String? ownerEmail;
  final String? ownerPhone;

  factory TenantSummary.fromJson(Map<String, dynamic> json) {
    return TenantSummary(
      id: json['id'] as String,
      name: json['name'] as String,
      slug: json['slug'] as String,
      businessType: json['businessType'] as String,
      status: json['status'] as String,
      modules: (json['modules'] as List<dynamic>? ?? const []).map((e) => e as String).toList(),
      ownerId: json['ownerId'] as String?,
      planCode: json['planCode'] as String?,
      maxMembers: (json['maxMembers'] as num?)?.toInt(),
      maxStaff: (json['maxStaff'] as num?)?.toInt(),
      timezone: json['timezone'] as String?,
      currency: json['currency'] as String?,
      hasLogo: json['hasLogo'] as bool? ?? false,
      logoBase64: json['logoBase64'] as String?,
      displayName: json['displayName'] as String?,
      accentColor: json['accentColor'] as String?,
      trialEndsAt: json['trialEndsAt'] as String?,
      graceDays: (json['graceDays'] as num?)?.toInt(),
      billingStatus: json['billingStatus'] as String?,
      customMaxMembers: (json['customMaxMembers'] as num?)?.toInt(),
      customMaxStaff: (json['customMaxStaff'] as num?)?.toInt(),
      phone: json['phone'] as String?,
      whatsappNumber: json['whatsappNumber'] as String?,
      memberCount: (json['memberCount'] as num?)?.toInt(),
      staffCount: (json['staffCount'] as num?)?.toInt(),
      lastLoginAt: json['lastLoginAt'] as String?,
      lastCollectionOn: json['lastCollectionOn'] as String?,
      ownerFullName: json['ownerFullName'] as String?,
      ownerEmail: json['ownerEmail'] as String?,
      ownerPhone: json['ownerPhone'] as String?,
    );
  }
}

class PublicBranding {
  PublicBranding({
    required this.name,
    required this.displayName,
    required this.slug,
    this.accentColor,
    this.hasLogo = false,
    this.logoUrl,
  });

  final String name;
  final String displayName;
  final String slug;
  final String? accentColor;
  final bool hasLogo;
  final String? logoUrl;

  factory PublicBranding.fromJson(Map<String, dynamic> json) {
    return PublicBranding(
      name: json['name'] as String,
      displayName: json['displayName'] as String? ?? json['name'] as String,
      slug: json['slug'] as String,
      accentColor: json['accentColor'] as String?,
      hasLogo: json['hasLogo'] as bool? ?? false,
      logoUrl: json['logoUrl'] as String?,
    );
  }
}

class Customer {
  Customer({
    required this.id,
    required this.customerCode,
    required this.fullName,
    this.phone,
    this.email,
    required this.status,
    this.notes,
    this.dueDate,
    this.createdAt,
    this.feePlanId,
    this.feePlanName,
    this.hasWhatsapp = true,
    this.branchId,
    this.branchName,
  });

  final String id;
  final String customerCode;
  final String fullName;
  final String? phone;
  final String? email;
  final String status;
  final String? notes;
  final String? dueDate;
  final String? createdAt;
  final String? feePlanId;
  final String? feePlanName;
  final bool hasWhatsapp;
  final String? branchId;
  final String? branchName;

  bool get isOverdue {
    if (dueDate == null || dueDate!.isEmpty) {
      return false;
    }
    final due = DateTime.tryParse(dueDate!);
    if (due == null) {
      return false;
    }
    final today = DateTime.now();
    final dueDay = DateTime(due.year, due.month, due.day);
    final todayDay = DateTime(today.year, today.month, today.day);
    return dueDay.isBefore(todayDay);
  }

  factory Customer.fromJson(Map<String, dynamic> json) {
    return Customer(
      id: _asString(json['id']) ?? '',
      customerCode: _asString(json['customerCode']) ?? '',
      fullName: _asString(json['fullName']) ?? '',
      phone: _asString(json['phone']),
      email: _asString(json['email']),
      status: _asString(json['status']) ?? '',
      notes: _asString(json['notes']),
      dueDate: _asString(json['dueDate']),
      createdAt: _asString(json['createdAt']),
      feePlanId: _asString(json['feePlanId']),
      feePlanName: _asString(json['feePlanName']),
      hasWhatsapp: json['hasWhatsapp'] as bool? ?? true,
      branchId: _asString(json['branchId']),
      branchName: _asString(json['branchName']),
    );
  }
}

String? _asString(Object? value) {
  if (value == null) {
    return null;
  }
  if (value is String) {
    return value;
  }
  return value.toString();
}

class FeePlan {
  FeePlan({
    required this.id,
    required this.name,
    required this.amountMinor,
    required this.currency,
    required this.amountLabel,
    required this.billingCycle,
    required this.graceDays,
    required this.isDefault,
  });

  final String id;
  final String name;
  final int amountMinor;
  final String currency;
  final String amountLabel;
  final String billingCycle;
  final int graceDays;
  final bool isDefault;

  factory FeePlan.fromJson(Map<String, dynamic> json) {
    return FeePlan(
      id: json['id'] as String,
      name: json['name'] as String,
      amountMinor: (json['amountMinor'] as num).toInt(),
      currency: json['currency'] as String,
      amountLabel: json['amountLabel'] as String,
      billingCycle: json['billingCycle'] as String,
      graceDays: (json['graceDays'] as num).toInt(),
      isDefault: json['isDefault'] as bool? ?? false,
    );
  }
}

class PendingFee {
  PendingFee({
    required this.id,
    required this.customerId,
    required this.customerName,
    required this.customerCode,
    this.phone,
    this.hasWhatsapp = true,
    required this.dueDate,
    required this.grossMinor,
    required this.paidMinor,
    required this.outstandingMinor,
    required this.currency,
    required this.outstandingLabel,
    required this.status,
    required this.effectiveStatus,
    this.receiptNo,
    this.planName,
    this.branchName,
  });

  final String id;
  final String customerId;
  final String customerName;
  final String customerCode;
  final String? phone;
  final bool hasWhatsapp;
  final String dueDate;
  final int grossMinor;
  final int paidMinor;
  final int outstandingMinor;
  final String currency;
  final String outstandingLabel;
  final String status;
  final String effectiveStatus;
  final String? receiptNo;
  final String? planName;
  final String? branchName;

  bool get isOverdue => effectiveStatus == 'OVERDUE';

  factory PendingFee.fromJson(Map<String, dynamic> json) {
    return PendingFee(
      id: json['id'] as String,
      customerId: json['customerId'] as String,
      customerName: json['customerName'] as String,
      customerCode: json['customerCode'] as String,
      phone: json['phone'] as String?,
      hasWhatsapp: json['hasWhatsapp'] as bool? ?? true,
      dueDate: json['dueDate'] as String,
      grossMinor: (json['grossMinor'] as num).toInt(),
      paidMinor: (json['paidMinor'] as num).toInt(),
      outstandingMinor: (json['outstandingMinor'] as num).toInt(),
      currency: json['currency'] as String,
      outstandingLabel: json['outstandingLabel'] as String,
      status: json['status'] as String,
      effectiveStatus: json['effectiveStatus'] as String,
      receiptNo: json['receiptNo'] as String?,
      planName: json['planName'] as String?,
      branchName: json['branchName'] as String?,
    );
  }
}

class PendingSummary {
  PendingSummary({
    required this.allCount,
    required this.allMinor,
    required this.allLabel,
    required this.overdueCount,
    required this.overdueMinor,
    required this.overdueLabel,
    required this.todayCount,
    required this.todayMinor,
    required this.todayLabel,
    required this.currency,
  });

  final int allCount;
  final int allMinor;
  final String allLabel;
  final int overdueCount;
  final int overdueMinor;
  final String overdueLabel;
  final int todayCount;
  final int todayMinor;
  final String todayLabel;
  final String currency;

  factory PendingSummary.fromJson(Map<String, dynamic> json) {
    return PendingSummary(
      allCount: (json['allCount'] as num).toInt(),
      allMinor: (json['allMinor'] as num).toInt(),
      allLabel: json['allLabel'] as String,
      overdueCount: (json['overdueCount'] as num).toInt(),
      overdueMinor: (json['overdueMinor'] as num).toInt(),
      overdueLabel: json['overdueLabel'] as String,
      todayCount: (json['todayCount'] as num).toInt(),
      todayMinor: (json['todayMinor'] as num).toInt(),
      todayLabel: json['todayLabel'] as String,
      currency: json['currency'] as String,
    );
  }

  static PendingSummary empty() => PendingSummary(
        allCount: 0,
        allMinor: 0,
        allLabel: '₹0.00',
        overdueCount: 0,
        overdueMinor: 0,
        overdueLabel: '₹0.00',
        todayCount: 0,
        todayMinor: 0,
        todayLabel: '₹0.00',
        currency: 'INR',
      );
}

class RemindPayload {
  RemindPayload({
    required this.channel,
    required this.body,
    required this.waLink,
    required this.smsLink,
    this.tenantWhatsapp,
    this.tenantPhone,
    this.memberName,
  });

  final String channel;
  final String body;
  final String waLink;
  final String smsLink;
  final String? tenantWhatsapp;
  final String? tenantPhone;
  final String? memberName;

  bool get preferWhatsapp => channel == 'WHATSAPP';

  factory RemindPayload.fromJson(Map<String, dynamic> json) {
    return RemindPayload(
      channel: json['channel'] as String,
      body: json['body'] as String,
      waLink: json['waLink'] as String,
      smsLink: json['smsLink'] as String,
      tenantWhatsapp: json['tenantWhatsapp'] as String?,
      tenantPhone: json['tenantPhone'] as String?,
      memberName: json['memberName'] as String?,
    );
  }
}

class TenantContact {
  TenantContact({this.name, this.phone, this.whatsappNumber});

  final String? name;
  final String? phone;
  final String? whatsappNumber;

  factory TenantContact.fromJson(Map<String, dynamic> json) {
    return TenantContact(
      name: json['name'] as String?,
      phone: json['phone'] as String?,
      whatsappNumber: json['whatsappNumber'] as String?,
    );
  }
}

class ReportOverview {
  ReportOverview({
    required this.members,
    required this.pendingCount,
    required this.outstandingMinor,
    required this.outstandingLabel,
    required this.overdueCount,
    required this.overdueMinor,
    required this.overdueLabel,
    required this.collectedTodayMinor,
    required this.collectedTodayLabel,
    required this.collectedMonthMinor,
    required this.collectedMonthLabel,
    required this.feesTodayMinor,
    required this.feesTodayLabel,
    required this.feesMonthMinor,
    required this.feesMonthLabel,
    required this.extrasTodayMinor,
    required this.extrasTodayLabel,
    required this.extrasMonthMinor,
    required this.extrasMonthLabel,
    required this.currency,
    required this.byPlan,
    required this.recentPayments,
    this.periods = const [],
  });

  final int members;
  final int pendingCount;
  final int outstandingMinor;
  final String outstandingLabel;
  final int overdueCount;
  final int overdueMinor;
  final String overdueLabel;
  final int collectedTodayMinor;
  final String collectedTodayLabel;
  final int collectedMonthMinor;
  final String collectedMonthLabel;
  final int feesTodayMinor;
  final String feesTodayLabel;
  final int feesMonthMinor;
  final String feesMonthLabel;
  final int extrasTodayMinor;
  final String extrasTodayLabel;
  final int extrasMonthMinor;
  final String extrasMonthLabel;
  final String currency;
  final List<PlanBreakdown> byPlan;
  final List<RecentPayment> recentPayments;
  final List<ReportPeriod> periods;

  factory ReportOverview.fromJson(Map<String, dynamic> json) {
    return ReportOverview(
      members: (json['members'] as num).toInt(),
      pendingCount: (json['pendingCount'] as num).toInt(),
      outstandingMinor: (json['outstandingMinor'] as num).toInt(),
      outstandingLabel: json['outstandingLabel'] as String,
      overdueCount: (json['overdueCount'] as num).toInt(),
      overdueMinor: (json['overdueMinor'] as num).toInt(),
      overdueLabel: json['overdueLabel'] as String,
      collectedTodayMinor: (json['collectedTodayMinor'] as num).toInt(),
      collectedTodayLabel: json['collectedTodayLabel'] as String,
      collectedMonthMinor: (json['collectedMonthMinor'] as num).toInt(),
      collectedMonthLabel: json['collectedMonthLabel'] as String,
      feesTodayMinor: (json['feesTodayMinor'] as num?)?.toInt() ?? 0,
      feesTodayLabel: json['feesTodayLabel'] as String? ?? '₹0.00',
      feesMonthMinor: (json['feesMonthMinor'] as num?)?.toInt() ?? 0,
      feesMonthLabel: json['feesMonthLabel'] as String? ?? '₹0.00',
      extrasTodayMinor: (json['extrasTodayMinor'] as num?)?.toInt() ?? 0,
      extrasTodayLabel: json['extrasTodayLabel'] as String? ?? '₹0.00',
      extrasMonthMinor: (json['extrasMonthMinor'] as num?)?.toInt() ?? 0,
      extrasMonthLabel: json['extrasMonthLabel'] as String? ?? '₹0.00',
      currency: json['currency'] as String,
      byPlan: (json['byPlan'] as List<dynamic>? ?? const [])
          .map((e) => PlanBreakdown.fromJson(e as Map<String, dynamic>))
          .toList(),
      recentPayments: (json['recentPayments'] as List<dynamic>? ?? const [])
          .map((e) => RecentPayment.fromJson(e as Map<String, dynamic>))
          .toList(),
      periods: (json['periods'] as List<dynamic>? ?? const [])
          .map((e) => ReportPeriod.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }

  static ReportOverview empty() => ReportOverview(
        members: 0,
        pendingCount: 0,
        outstandingMinor: 0,
        outstandingLabel: '₹0.00',
        overdueCount: 0,
        overdueMinor: 0,
        overdueLabel: '₹0.00',
        collectedTodayMinor: 0,
        collectedTodayLabel: '₹0.00',
        collectedMonthMinor: 0,
        collectedMonthLabel: '₹0.00',
        feesTodayMinor: 0,
        feesTodayLabel: '₹0.00',
        feesMonthMinor: 0,
        feesMonthLabel: '₹0.00',
        extrasTodayMinor: 0,
        extrasTodayLabel: '₹0.00',
        extrasMonthMinor: 0,
        extrasMonthLabel: '₹0.00',
        currency: 'INR',
        byPlan: const [],
        recentPayments: const [],
        periods: const [],
      );
}

class ReportPeriod {
  ReportPeriod({required this.year, required this.month});

  final int year;
  final int month;

  String get label {
    const names = [
      'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
      'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
    ];
    final name = month >= 1 && month <= 12 ? names[month - 1] : '$month';
    return '$name $year';
  }

  factory ReportPeriod.fromJson(Map<String, dynamic> json) {
    return ReportPeriod(
      year: (json['year'] as num).toInt(),
      month: (json['month'] as num).toInt(),
    );
  }
}

class PlanBreakdown {
  PlanBreakdown({
    required this.name,
    required this.feeCount,
    required this.outstandingMinor,
    required this.outstandingLabel,
  });

  final String name;
  final int feeCount;
  final int outstandingMinor;
  final String outstandingLabel;

  factory PlanBreakdown.fromJson(Map<String, dynamic> json) {
    return PlanBreakdown(
      name: json['name'] as String,
      feeCount: (json['feeCount'] as num).toInt(),
      outstandingMinor: (json['outstandingMinor'] as num).toInt(),
      outstandingLabel: json['outstandingLabel'] as String,
    );
  }
}

class RecentPayment {
  RecentPayment({
    required this.receiptNo,
    required this.customerName,
    required this.amountMinor,
    required this.amountLabel,
    required this.method,
    required this.paidOn,
    this.source = 'FEE',
    this.category = '',
    this.branchName = '',
  });

  final String receiptNo;
  final String customerName;
  final int amountMinor;
  final String amountLabel;
  final String method;
  final String paidOn;
  final String source;
  final String category;
  final String branchName;

  bool get isExtra => source == 'EXTRA';

  factory RecentPayment.fromJson(Map<String, dynamic> json) {
    return RecentPayment(
      receiptNo: json['receiptNo'] as String,
      customerName: json['customerName'] as String,
      amountMinor: (json['amountMinor'] as num).toInt(),
      amountLabel: json['amountLabel'] as String,
      method: json['method'] as String,
      paidOn: json['paidOn'] as String,
      source: json['source'] as String? ?? 'FEE',
      category: json['category'] as String? ?? '',
      branchName: json['branchName'] as String? ?? '',
    );
  }
}

class PaymentRecord {
  PaymentRecord({
    required this.id,
    required this.receiptNo,
    required this.customerName,
    required this.customerCode,
    required this.amountMinor,
    required this.currency,
    required this.amountLabel,
    required this.method,
    this.referenceNo,
    required this.paidOn,
    required this.status,
    this.voidReason,
  });

  final String id;
  final String receiptNo;
  final String customerName;
  final String customerCode;
  final int amountMinor;
  final String currency;
  final String amountLabel;
  final String method;
  final String? referenceNo;
  final String paidOn;
  final String status;
  final String? voidReason;

  bool get isVoid => status == 'VOID';

  factory PaymentRecord.fromJson(Map<String, dynamic> json) {
    return PaymentRecord(
      id: json['id'] as String,
      receiptNo: json['receiptNo'] as String,
      customerName: json['customerName'] as String,
      customerCode: json['customerCode'] as String,
      amountMinor: (json['amountMinor'] as num).toInt(),
      currency: json['currency'] as String,
      amountLabel: json['amountLabel'] as String,
      method: json['method'] as String,
      referenceNo: json['referenceNo'] as String?,
      paidOn: json['paidOn'] as String,
      status: json['status'] as String,
      voidReason: json['voidReason'] as String?,
    );
  }
}

class ImportResult {
  ImportResult({required this.created, required this.skipped, required this.errors});

  final int created;
  final int skipped;
  final List<String> errors;

  factory ImportResult.fromJson(Map<String, dynamic> json) {
    return ImportResult(
      created: (json['created'] as num).toInt(),
      skipped: (json['skipped'] as num).toInt(),
      errors: (json['errors'] as List<dynamic>? ?? const []).map((e) => e.toString()).toList(),
    );
  }
}

class Batch {
  Batch({required this.id, required this.name, this.schedule, required this.memberCount});

  final String id;
  final String name;
  final String? schedule;
  final int memberCount;

  factory Batch.fromJson(Map<String, dynamic> json) {
    return Batch(
      id: json['id'] as String,
      name: json['name'] as String,
      schedule: json['schedule'] as String?,
      memberCount: (json['memberCount'] as num).toInt(),
    );
  }
}

class AttendanceMember {
  AttendanceMember({
    required this.id,
    required this.customerCode,
    required this.fullName,
    this.phone,
    this.status,
  });

  final String id;
  final String customerCode;
  final String fullName;
  final String? phone;
  final String? status;

  factory AttendanceMember.fromJson(Map<String, dynamic> json) {
    return AttendanceMember(
      id: json['id'] as String,
      customerCode: json['customerCode'] as String,
      fullName: json['fullName'] as String,
      phone: json['phone'] as String?,
      status: json['status'] as String?,
    );
  }
}

class AttendanceRoster {
  AttendanceRoster({required this.batchId, required this.markedOn, required this.members});

  final String batchId;
  final String markedOn;
  final List<AttendanceMember> members;

  factory AttendanceRoster.fromJson(Map<String, dynamic> json) {
    return AttendanceRoster(
      batchId: json['batchId'] as String,
      markedOn: json['markedOn'] as String,
      members: (json['members'] as List<dynamic>? ?? const [])
          .map((e) => AttendanceMember.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

class PersonalCategory {
  PersonalCategory({required this.id, required this.name, required this.icon, required this.color, required this.active});

  final String id;
  final String name;
  final String icon;
  final String color;
  final bool active;

  factory PersonalCategory.fromJson(Map<String, dynamic> json) {
    return PersonalCategory(
      id: json['id'] as String,
      name: json['name'] as String,
      icon: json['icon'] as String? ?? 'category',
      color: json['color'] as String? ?? '#0F766E',
      active: json['active'] as bool? ?? true,
    );
  }
}

class PersonalExpense {
  PersonalExpense({
    required this.id,
    required this.amountMinor,
    this.description,
    required this.occurredOn,
    this.method,
    required this.categoryId,
    required this.categoryName,
  });

  final String id;
  final int amountMinor;
  final String? description;
  final String occurredOn;
  final String? method;
  final String categoryId;
  final String categoryName;

  factory PersonalExpense.fromJson(Map<String, dynamic> json) {
    return PersonalExpense(
      id: json['id'] as String,
      amountMinor: (json['amountMinor'] as num).toInt(),
      description: json['description'] as String?,
      occurredOn: json['occurredOn'] as String,
      method: json['method'] as String?,
      categoryId: json['categoryId'] as String,
      categoryName: json['categoryName'] as String,
    );
  }
}

class PersonalIncome {
  PersonalIncome({required this.id, required this.amountMinor, required this.source, this.description, required this.occurredOn});

  final String id;
  final int amountMinor;
  final String source;
  final String? description;
  final String occurredOn;

  factory PersonalIncome.fromJson(Map<String, dynamic> json) {
    return PersonalIncome(
      id: json['id'] as String,
      amountMinor: (json['amountMinor'] as num).toInt(),
      source: json['source'] as String,
      description: json['description'] as String?,
      occurredOn: json['occurredOn'] as String,
    );
  }
}

class PersonalBudget {
  PersonalBudget({
    required this.id,
    required this.limitMinor,
    required this.spentMinor,
    required this.categoryId,
    required this.categoryName,
  });

  final String id;
  final int limitMinor;
  final int spentMinor;
  final String categoryId;
  final String categoryName;

  double get percent => limitMinor == 0 ? 0 : spentMinor / limitMinor;

  factory PersonalBudget.fromJson(Map<String, dynamic> json) {
    return PersonalBudget(
      id: json['id'] as String,
      limitMinor: (json['limitMinor'] as num).toInt(),
      spentMinor: (json['spentMinor'] as num).toInt(),
      categoryId: json['categoryId'] as String,
      categoryName: json['categoryName'] as String,
    );
  }
}

class PersonalSummary {
  PersonalSummary({
    required this.month,
    required this.incomeMinor,
    required this.expenseMinor,
    required this.savedMinor,
    required this.savedPercent,
    required this.byCategory,
    required this.budgets,
  });

  final String month;
  final int incomeMinor;
  final int expenseMinor;
  final int savedMinor;
  final int savedPercent;
  final List<CategorySpend> byCategory;
  final List<PersonalBudget> budgets;

  factory PersonalSummary.fromJson(Map<String, dynamic> json) {
    return PersonalSummary(
      month: json['month'] as String,
      incomeMinor: (json['incomeMinor'] as num).toInt(),
      expenseMinor: (json['expenseMinor'] as num).toInt(),
      savedMinor: (json['savedMinor'] as num).toInt(),
      savedPercent: (json['savedPercent'] as num).toInt(),
      byCategory: (json['byCategory'] as List<dynamic>? ?? const [])
          .map((e) => CategorySpend.fromJson(e as Map<String, dynamic>))
          .toList(),
      budgets: (json['budgets'] as List<dynamic>? ?? const [])
          .map((e) => PersonalBudget.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

class CategorySpend {
  CategorySpend({required this.name, required this.color, required this.spentMinor});

  final String name;
  final String color;
  final int spentMinor;

  factory CategorySpend.fromJson(Map<String, dynamic> json) {
    return CategorySpend(
      name: json['name'] as String,
      color: json['color'] as String? ?? '#0F766E',
      spentMinor: (json['spentMinor'] as num).toInt(),
    );
  }
}

class ExpenseGroup {
  ExpenseGroup({required this.id, required this.name, required this.type});

  final String id;
  final String name;
  final String type;

  factory ExpenseGroup.fromJson(Map<String, dynamic> json) {
    return ExpenseGroup(
      id: json['id'] as String,
      name: json['name'] as String,
      type: json['type'] as String? ?? 'CUSTOM',
    );
  }
}

class GroupMember {
  GroupMember({required this.id, this.userId, required this.displayName, required this.status});

  final String id;
  final String? userId;
  final String displayName;
  final String status;

  factory GroupMember.fromJson(Map<String, dynamic> json) {
    return GroupMember(
      id: json['id'] as String,
      userId: json['userId'] as String?,
      displayName: json['displayName'] as String,
      status: json['status'] as String? ?? 'ACTIVE',
    );
  }
}

class GroupBalances {
  GroupBalances({
    required this.totalMinor,
    required this.myPaidMinor,
    required this.myNetMinor,
    required this.suggested,
    required this.paid,
    this.groupName = 'Group',
  });

  final String groupName;
  final int totalMinor;
  final int myPaidMinor;
  final int myNetMinor;
  final List<SettlementLine> suggested;
  final List<GroupMemberPaid> paid;

  factory GroupBalances.fromJson(Map<String, dynamic> json) {
    return GroupBalances(
      groupName: json['groupName'] as String? ?? 'Group',
      totalMinor: (json['totalMinor'] as num).toInt(),
      myPaidMinor: (json['myPaidMinor'] as num).toInt(),
      myNetMinor: (json['myNetMinor'] as num).toInt(),
      suggested: (json['suggested'] as List<dynamic>? ?? const [])
          .map((e) => SettlementLine.fromJson(e as Map<String, dynamic>))
          .toList(),
      paid: (json['paid'] as List<dynamic>? ?? const [])
          .map((e) => GroupMemberPaid.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

class GroupMemberPaid {
  GroupMemberPaid({required this.memberId, required this.name, required this.paidMinor});

  final String memberId;
  final String name;
  final int paidMinor;

  factory GroupMemberPaid.fromJson(Map<String, dynamic> json) {
    return GroupMemberPaid(
      memberId: json['memberId'] as String,
      name: json['name'] as String,
      paidMinor: (json['paidMinor'] as num).toInt(),
    );
  }
}

class SettlementLine {
  SettlementLine({
    required this.fromName,
    required this.toName,
    required this.amountMinor,
    required this.fromMemberId,
    required this.toMemberId,
    this.youPay = false,
    this.youReceive = false,
  });

  final String fromName;
  final String toName;
  final int amountMinor;
  final String fromMemberId;
  final String toMemberId;
  final bool youPay;
  final bool youReceive;

  factory SettlementLine.fromJson(Map<String, dynamic> json) {
    return SettlementLine(
      fromName: json['fromName'] as String,
      toName: json['toName'] as String,
      amountMinor: (json['amountMinor'] as num).toInt(),
      fromMemberId: json['fromMemberId'] as String,
      toMemberId: json['toMemberId'] as String,
      youPay: json['youPay'] as bool? ?? false,
      youReceive: json['youReceive'] as bool? ?? false,
    );
  }
}

class GroupExpenseItem {
  GroupExpenseItem({
    required this.id,
    required this.description,
    required this.amountMinor,
    required this.payerName,
    required this.paidBy,
    required this.canEdit,
    required this.shares,
  });

  final String id;
  final String description;
  final int amountMinor;
  final String payerName;
  final String paidBy;
  final bool canEdit;
  final List<GroupShare> shares;

  factory GroupExpenseItem.fromJson(Map<String, dynamic> json) {
    return GroupExpenseItem(
      id: json['id'] as String,
      description: json['description'] as String,
      amountMinor: (json['amountMinor'] as num).toInt(),
      payerName: json['payerName'] as String,
      paidBy: json['paidBy'] as String? ?? '',
      canEdit: json['canEdit'] as bool? ?? false,
      shares: (json['shares'] as List<dynamic>? ?? const [])
          .map((e) => GroupShare.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

class GroupShare {
  GroupShare({
    required this.memberId,
    required this.displayName,
    required this.shareMinor,
    required this.settled,
    required this.isPayer,
  });

  final String memberId;
  final String displayName;
  final int shareMinor;
  final bool settled;
  final bool isPayer;

  factory GroupShare.fromJson(Map<String, dynamic> json) {
    return GroupShare(
      memberId: json['memberId'] as String,
      displayName: json['displayName'] as String,
      shareMinor: (json['shareMinor'] as num).toInt(),
      settled: json['settled'] as bool? ?? false,
      isPayer: json['isPayer'] as bool? ?? false,
    );
  }
}

class GroupInvitePreview {
  GroupInvitePreview({
    required this.groupId,
    required this.groupName,
    required this.groupType,
    required this.memberCount,
    required this.invitedBy,
  });

  final String groupId;
  final String groupName;
  final String groupType;
  final int memberCount;
  final String invitedBy;

  factory GroupInvitePreview.fromJson(Map<String, dynamic> json) {
    return GroupInvitePreview(
      groupId: json['groupId'] as String,
      groupName: json['groupName'] as String,
      groupType: json['groupType'] as String? ?? 'CUSTOM',
      memberCount: (json['memberCount'] as num).toInt(),
      invitedBy: json['invitedBy'] as String? ?? 'A member',
    );
  }
}

class GroupInviteTicket {
  GroupInviteTicket({
    required this.token,
    required this.path,
    required this.groupName,
    required this.memberCount,
  });

  final String token;
  final String path;
  final String groupName;
  final int memberCount;

  factory GroupInviteTicket.fromJson(Map<String, dynamic> json) {
    return GroupInviteTicket(
      token: json['token'] as String,
      path: json['path'] as String,
      groupName: json['groupName'] as String? ?? 'Group',
      memberCount: (json['memberCount'] as num?)?.toInt() ?? 0,
    );
  }
}

class DuesMailSettings {
  DuesMailSettings({
    required this.enabled,
    required this.cronExpr,
    required this.timezone,
    this.smtpHost,
    required this.smtpPort,
    this.smtpUsername,
    required this.smtpPasswordSet,
    this.smtpFrom,
    this.lastRunAt,
    this.lastResult,
  });

  final bool enabled;
  final String cronExpr;
  final String timezone;
  final String? smtpHost;
  final int smtpPort;
  final String? smtpUsername;
  final bool smtpPasswordSet;
  final String? smtpFrom;
  final String? lastRunAt;
  final String? lastResult;

  factory DuesMailSettings.fromJson(Map<String, dynamic> json) {
    return DuesMailSettings(
      enabled: json['enabled'] as bool? ?? false,
      cronExpr: json['cronExpr'] as String? ?? '0 0 8 * * *',
      timezone: json['timezone'] as String? ?? 'Asia/Kolkata',
      smtpHost: json['smtpHost'] as String?,
      smtpPort: (json['smtpPort'] as num?)?.toInt() ?? 587,
      smtpUsername: json['smtpUsername'] as String?,
      smtpPasswordSet: json['smtpPasswordSet'] as bool? ?? false,
      smtpFrom: json['smtpFrom'] as String?,
      lastRunAt: json['lastRunAt'] as String?,
      lastResult: json['lastResult'] as String?,
    );
  }
}

class PlatformConfigRow {
  PlatformConfigRow({
    required this.id,
    required this.paramKey,
    required this.paramSubKey,
    required this.paramValue,
    this.description,
    required this.locked,
    this.createdAt,
    this.updatedAt,
  });

  final String id;
  final String paramKey;
  final String paramSubKey;
  final String paramValue;
  final String? description;
  final bool locked;
  final String? createdAt;
  final String? updatedAt;

  factory PlatformConfigRow.fromJson(Map<String, dynamic> json) {
    return PlatformConfigRow(
      id: json['id'].toString(),
      paramKey: json['paramKey'] as String? ?? '',
      paramSubKey: json['paramSubKey'] as String? ?? 'DEFAULT',
      paramValue: json['paramValue'] as String? ?? '',
      description: json['description'] as String?,
      locked: json['locked'] as bool? ?? false,
      createdAt: json['createdAt'] as String?,
      updatedAt: json['updatedAt'] as String?,
    );
  }
}

class IndividualAccount {
  IndividualAccount({
    required this.id,
    required this.fullName,
    this.email,
    this.phone,
    required this.status,
    this.lastLoginAt,
    this.createdAt,
    required this.groupMemberCount,
    required this.groupsJoined,
    required this.expenseCount,
  });

  final String id;
  final String fullName;
  final String? email;
  final String? phone;
  final String status;
  final String? lastLoginAt;
  final String? createdAt;
  final int groupMemberCount;
  final int groupsJoined;
  final int expenseCount;

  factory IndividualAccount.fromJson(Map<String, dynamic> json) {
    return IndividualAccount(
      id: json['id'].toString(),
      fullName: json['fullName'] as String? ?? '',
      email: json['email'] as String?,
      phone: json['phone'] as String?,
      status: json['status'] as String? ?? '',
      lastLoginAt: json['lastLoginAt'] as String?,
      createdAt: json['createdAt'] as String?,
      groupMemberCount: (json['groupMemberCount'] as num?)?.toInt() ?? 0,
      groupsJoined: (json['groupsJoined'] as num?)?.toInt() ?? 0,
      expenseCount: (json['expenseCount'] as num?)?.toInt() ?? 0,
    );
  }
}

class JoinEnquiry {
  JoinEnquiry({
    required this.id,
    required this.userId,
    required this.fullName,
    this.email,
    this.phone,
    required this.businessName,
    this.city,
    this.message,
    required this.status,
    this.createdAt,
  });

  final String id;
  final String userId;
  final String fullName;
  final String? email;
  final String? phone;
  final String businessName;
  final String? city;
  final String? message;
  final String status;
  final String? createdAt;

  factory JoinEnquiry.fromJson(Map<String, dynamic> json) {
    return JoinEnquiry(
      id: json['id'].toString(),
      userId: json['userId'].toString(),
      fullName: json['fullName'] as String? ?? '',
      email: json['email'] as String?,
      phone: json['phone'] as String?,
      businessName: json['businessName'] as String? ?? '',
      city: json['city'] as String?,
      message: json['message'] as String?,
      status: json['status'] as String? ?? '',
      createdAt: json['createdAt']?.toString(),
    );
  }
}

class SupportThread {
  SupportThread({
    required this.id,
    required this.userId,
    required this.fullName,
    this.email,
    this.lastBody,
    this.lastAt,
    this.unreadCount = 0,
  });

  final String id;
  final String userId;
  final String fullName;
  final String? email;
  final String? lastBody;
  final String? lastAt;
  final int unreadCount;

  factory SupportThread.fromJson(Map<String, dynamic> json) {
    return SupportThread(
      id: json['id'].toString(),
      userId: json['userId'].toString(),
      fullName: json['fullName'] as String? ?? '',
      email: json['email'] as String?,
      lastBody: json['lastBody'] as String?,
      lastAt: json['lastAt']?.toString(),
      unreadCount: (json['unreadCount'] as num?)?.toInt() ?? 0,
    );
  }
}

class SupportMessage {
  SupportMessage({
    required this.id,
    required this.threadId,
    this.authorUserId,
    required this.fromPlatform,
    required this.body,
    this.createdAt,
    this.authorName,
  });

  final String id;
  final String threadId;
  final String? authorUserId;
  final bool fromPlatform;
  final String body;
  final String? createdAt;
  final String? authorName;

  factory SupportMessage.fromJson(Map<String, dynamic> json) {
    return SupportMessage(
      id: json['id'].toString(),
      threadId: json['threadId'].toString(),
      authorUserId: json['authorUserId']?.toString(),
      fromPlatform: json['fromPlatform'] as bool? ?? false,
      body: json['body'] as String? ?? '',
      createdAt: json['createdAt']?.toString(),
      authorName: json['authorName'] as String?,
    );
  }
}





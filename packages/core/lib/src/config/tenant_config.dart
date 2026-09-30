import 'package:feesaas_api_client/feesaas_api_client.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class TenantConfig {
  TenantConfig(this.bootstrap);

  final BootstrapResponse bootstrap;

  UserSummary get user => bootstrap.user;
  TenantBootstrap? get tenant => bootstrap.tenant;
  List<String> get permissions => bootstrap.permissions;
  List<String> get modules => tenant?.modules ?? const [];
  bool get isPlatform => user.role == 'PLATFORM_SUPER_ADMIN';
  bool get isPersonal => bootstrap.activeContext?.kind == 'PERSONAL' || (tenant == null && !isPlatform);
  bool get isSuspended => tenant?.status == 'SUSPENDED' || tenant?.status == 'CANCELLED';
  bool get needsOnboarding => bootstrap.needsOnboarding;

  String get homeLocation {
    if (needsOnboarding) {
      return '/onboarding';
    }
    final kind = bootstrap.activeContext?.kind;
    if (kind == 'PERSONAL' || (kind == null && isPersonal)) {
      return '/personal';
    }
    if (kind == 'GROUP' && bootstrap.activeContext?.groupId != null) {
      return '/groups/${bootstrap.activeContext!.groupId}';
    }
    return '/pending';
  }

  bool hasModule(String code) => modules.contains(code);
  bool hasPermission(String code) {
    if (isPlatform) {
      return permissions.contains(code);
    }
    if (user.role == 'BUSINESS_OWNER' && !code.startsWith('platform.')) {
      return true;
    }
    return permissions.contains(code);
  }

  String label(String key, String fallback) => tenant?.label(key, fallback) ?? fallback;
}

final tenantConfigProvider = StateProvider<TenantConfig?>((ref) => null);

final publicBrandingProvider = StateProvider<PublicBranding?>((ref) => null);

Color? parseAccent(String? hex) {
  if (hex == null || hex.isEmpty || !hex.startsWith('#') || hex.length != 7) {
    return null;
  }
  final value = int.tryParse(hex.substring(1), radix: 16);
  if (value == null) {
    return null;
  }
  return Color(0xFF000000 | value);
}

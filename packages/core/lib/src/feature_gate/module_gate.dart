import 'package:feesaas_core/src/config/tenant_config.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class ModuleGate extends ConsumerWidget {
  const ModuleGate({super.key, required this.module, required this.child, this.fallback});

  final String module;
  final Widget child;
  final Widget? fallback;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(tenantConfigProvider);
    if (config == null || !config.hasModule(module)) {
      return fallback ?? const SizedBox.shrink();
    }
    return child;
  }
}

class PermissionGate extends ConsumerWidget {
  const PermissionGate({super.key, required this.permission, required this.child, this.fallback});

  final String permission;
  final Widget child;
  final Widget? fallback;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(tenantConfigProvider);
    if (config == null || !config.hasPermission(permission)) {
      return fallback ?? const SizedBox.shrink();
    }
    return child;
  }
}

import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/features/auth/workspace_swap.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

export 'workspace_swap.dart';

UserContext? otherWorkspace(TenantConfig? config) {
  final contexts = config?.bootstrap.contexts ?? const <UserContext>[];
  final want = config?.bootstrap.activeContext?.kind == 'PERSONAL' ? 'BUSINESS' : 'PERSONAL';
  for (final c in contexts) {
    if (c.kind == want) {
      return c;
    }
  }
  return null;
}

class WorkspaceSwitcher extends ConsumerWidget {
  const WorkspaceSwitcher({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(tenantConfigProvider);
    final other = otherWorkspace(config);
    if (other == null) {
      return const SizedBox.shrink();
    }
    final toMoney = other.kind == 'PERSONAL';
    return IconButton(
      tooltip: toMoney ? 'My money' : other.label,
      onPressed: () => swapWorkspace(context, ref, other),
      icon: Icon(toMoney ? Icons.savings_outlined : Icons.storefront_outlined),
    );
  }
}

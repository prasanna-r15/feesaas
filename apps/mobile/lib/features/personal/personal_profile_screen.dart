import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/features/auth/workspace_switcher.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class PersonalProfileScreen extends ConsumerWidget {
  const PersonalProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(tenantConfigProvider);
    final hat = otherWorkspace(config);
    final showEnquiry = config != null && !config.hasBusinessHat;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Profile'),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(config?.user.fullName ?? '', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)),
          const SizedBox(height: 16),
          if (hat != null)
            FsCard(
              child: ListTile(
                leading: Icon(hat.kind == 'PERSONAL' ? Icons.savings_outlined : Icons.storefront_outlined),
                title: Text(hat.kind == 'PERSONAL' ? 'My money' : hat.label),
                subtitle: Text(hat.kind == 'PERSONAL'
                    ? 'Expenses, income, budgets, and group split'
                    : 'Members, fees, and attendance'),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => swapWorkspace(context, ref, hat),
              ),
            ),
          if (hat != null) const SizedBox(height: 16),
          if (showEnquiry) ...[
            FsCard(
              child: ListTile(
                leading: const Icon(Icons.storefront_outlined),
                title: const Text('Business enquiry'),
                subtitle: const Text('Ask admin to join a gym or business'),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => context.push('/personal/profile/enquiry'),
              ),
            ),
            const SizedBox(height: 16),
          ],
          FsCard(
            child: ListTile(
              leading: FsUnreadBadge(
                count: ref.watch(mySupportUnreadProvider),
                child: const Icon(Icons.chat_outlined),
              ),
              title: const Text('Chat with admin'),
              subtitle: const Text('Message DueMate support'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => context.push('/personal/profile/support'),
            ),
          ),
          const SizedBox(height: 16),
          FsCard(
            child: ListTile(
              leading: const Icon(Icons.logout),
              title: const Text('Sign out'),
              onTap: () => ref.read(sessionControllerProvider.notifier).logout(),
            ),
          ),
        ],
      ),
    );
  }
}

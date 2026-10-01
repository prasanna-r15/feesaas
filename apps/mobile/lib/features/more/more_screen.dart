import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/features/auth/workspace_switcher.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class MoreScreen extends ConsumerWidget {
  const MoreScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(tenantConfigProvider);
    final scheme = Theme.of(context).colorScheme;
    final hat = _otherHat(config);
    return Scaffold(
      appBar: AppBar(
        leading: const Padding(padding: EdgeInsets.all(6), child: DueMateLogo(height: 36)),
        title: const Text('More'),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        children: [
          FsEnter(
            child: FsCard(
              child: ListTile(
                contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                leading: CircleAvatar(
                  backgroundColor: scheme.primaryContainer,
                  child: Text(
                    (config?.user.fullName.isNotEmpty == true ? config!.user.fullName[0] : 'U').toUpperCase(),
                    style: TextStyle(color: scheme.onPrimaryContainer, fontWeight: FontWeight.w800),
                  ),
                ),
                title: Text(config?.user.fullName ?? '', style: const TextStyle(fontWeight: FontWeight.w700)),
                subtitle: Text(config?.tenant?.title ?? config?.user.role ?? ''),
              ),
            ),
          ),
          const SizedBox(height: 16),
          if (hat != null)
            _MoreTile(
              delay: Duration.zero,
              icon: hat.kind == 'PERSONAL' ? Icons.savings_outlined : Icons.storefront_outlined,
              title: hat.kind == 'PERSONAL' ? 'My money' : hat.label,
              subtitle: hat.kind == 'PERSONAL'
                  ? 'Expenses, income, budgets, and group split'
                  : 'Members, fees, and attendance',
              onTap: () => swapWorkspace(context, ref, hat),
            ),
          PermissionGate(
            permission: 'settings.manage',
            child: _MoreTile(
              delay: const Duration(milliseconds: 40),
              icon: Icons.phone_outlined,
              title: 'Business numbers',
              subtitle: 'WhatsApp and SMS for reminders',
              onTap: () => context.go('/more/contact'),
            ),
          ),
          PermissionGate(
            permission: 'fees.manage',
            child: _MoreTile(
              delay: const Duration(milliseconds: 80),
              icon: Icons.sell_outlined,
              title: 'Fee plans',
              subtitle: 'General, Cardio, PT, and custom plans',
              onTap: () => context.go('/more/fee-plans'),
            ),
          ),
          PermissionGate(
            permission: 'payments.view',
            child: _MoreTile(
              delay: const Duration(milliseconds: 120),
              icon: Icons.receipt_long_outlined,
              title: 'Receipts',
              subtitle: 'Cash, UPI, card — void if needed',
              onTap: () => context.go('/more/payments'),
            ),
          ),
          ModuleGate(
            module: 'ATTENDANCE',
            child: PermissionGate(
              permission: 'batches.manage',
              child: _MoreTile(
                delay: const Duration(milliseconds: 160),
                icon: Icons.groups_outlined,
                title: 'Batches & attendance',
                onTap: () => context.go('/more/batches'),
              ),
            ),
          ),
          ModuleGate(
            module: 'BRANCHES',
            child: PermissionGate(
              permission: 'customers.view',
              child: _MoreTile(
                delay: const Duration(milliseconds: 170),
                icon: Icons.location_on_outlined,
                title: 'Locations',
                subtitle: 'Karamadai, Teachers Colony, and other branches',
                onTap: () => context.go('/more/branches'),
              ),
            ),
          ),
          ModuleGate(
            module: 'ADDONS',
            child: PermissionGate(
              permission: 'payments.view',
              child: _MoreTile(
                delay: const Duration(milliseconds: 180),
                icon: Icons.shopping_bag_outlined,
                title: 'Extras',
                subtitle: 'Protein, diet packs, and other collections',
                onTap: () => context.go('/more/addons'),
              ),
            ),
          ),
          ModuleGate(
            module: 'DIET_CHARTS',
            child: PermissionGate(
              permission: 'reminders.send',
              child: _MoreTile(
                delay: const Duration(milliseconds: 190),
                icon: Icons.restaurant_outlined,
                title: 'Diet charts',
                subtitle: 'Send a chart to members on WhatsApp',
                onTap: () => context.go('/more/diet-charts'),
              ),
            ),
          ),
          PermissionGate(
            permission: 'imports.run',
            child: _MoreTile(
              delay: const Duration(milliseconds: 160),
              icon: Icons.file_upload_outlined,
              title: 'Import members',
              subtitle: 'CSV template for new gyms',
              onTap: () => context.go('/more/import'),
            ),
          ),
          PermissionGate(
            permission: 'platform.tenants.manage',
            child: _MoreTile(
              delay: const Duration(milliseconds: 200),
              icon: Icons.storefront_outlined,
              title: 'Businesses & plans',
              subtitle: 'FREE vs PRO member limits',
              onTap: () => context.go('/more/gyms'),
            ),
          ),
          PermissionGate(
            permission: 'platform.tenants.manage',
            child: _MoreTile(
              delay: const Duration(milliseconds: 210),
              icon: Icons.email_outlined,
              title: 'Pending dues email',
              subtitle: 'Cron + free SMTP digest to owners',
              onTap: () => context.go('/more/mail'),
            ),
          ),
          PermissionGate(
            permission: 'staff.manage',
            child: _MoreTile(
              delay: const Duration(milliseconds: 200),
              icon: Icons.badge_outlined,
              title: 'Staff',
              onTap: () => context.go('/more/staff'),
            ),
          ),
          _MoreTile(
            delay: const Duration(milliseconds: 220),
            icon: Icons.chat_outlined,
            title: 'Chat with admin',
            subtitle: 'Message DueMate support',
            unread: ref.watch(mySupportUnreadProvider),
            onTap: () => context.push('/more/support'),
          ),
          const SizedBox(height: 8),
          FsEnter(
            delay: const Duration(milliseconds: 240),
            child: FsCard(
              onTap: () => ref.read(sessionControllerProvider.notifier).logout(),
              child: const ListTile(
                leading: Icon(Icons.logout),
                title: Text('Sign out'),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

UserContext? _otherHat(TenantConfig? config) => otherWorkspace(config);

class _MoreTile extends StatelessWidget {
  const _MoreTile({
    required this.icon,
    required this.title,
    required this.onTap,
    this.subtitle,
    this.delay = Duration.zero,
    this.unread = 0,
  });

  final IconData icon;
  final String title;
  final String? subtitle;
  final VoidCallback onTap;
  final Duration delay;
  final int unread;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: FsEnter(
        delay: delay,
        child: FsCard(
          onTap: onTap,
          child: ListTile(
            leading: FsUnreadBadge(count: unread, child: Icon(icon)),
            title: Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
            subtitle: subtitle == null ? null : Text(subtitle!),
            trailing: const Icon(Icons.chevron_right),
          ),
        ),
      ),
    );
  }
}

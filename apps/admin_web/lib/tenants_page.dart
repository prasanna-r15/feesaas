import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

final tenantFilterProvider = StateProvider<({String q, String status, String plan, String type})>(
  (ref) => (q: '', status: '', plan: '', type: ''),
);

final tenantsProvider = FutureProvider.autoDispose((ref) {
  final filter = ref.watch(tenantFilterProvider);
  return ref.watch(platformApiProvider).listTenants(
        q: filter.q,
        status: filter.status,
        plan: filter.plan,
        businessType: filter.type,
      );
});

class TenantsPage extends ConsumerStatefulWidget {
  const TenantsPage({super.key});

  @override
  ConsumerState<TenantsPage> createState() => _TenantsPageState();
}

class _TenantsPageState extends ConsumerState<TenantsPage> {
  final _search = TextEditingController();

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(tenantsProvider);
    final filter = ref.watch(tenantFilterProvider);
    return Scaffold(
      appBar: AppBar(
        leading: const Padding(padding: EdgeInsets.all(6), child: DueMateLogo(height: 36)),
        title: const Text('Tenants'),
        actions: [
          TextButton(
            onPressed: () => context.push('/mail'),
            child: const Text('Dues email'),
          ),
          TextButton(
            onPressed: () => context.push('/config'),
            child: const Text('Config'),
          ),
          TextButton(
            onPressed: () => ref.read(sessionControllerProvider.notifier).logout(),
            child: const Text('Sign out'),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push('/tenants/new'),
        label: const Text('New tenant'),
        icon: const Icon(Icons.add),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
            child: TextField(
              controller: _search,
              decoration: const InputDecoration(
                prefixIcon: Icon(Icons.search),
                labelText: 'Search name or slug',
              ),
              onChanged: (v) => ref.read(tenantFilterProvider.notifier).state = (
                q: v,
                status: filter.status,
                plan: filter.plan,
                type: filter.type,
              ),
            ),
          ),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            child: Row(
              children: [
                _chip('All status', filter.status.isEmpty, () => _set(status: '')),
                _chip('ACTIVE', filter.status == 'ACTIVE', () => _set(status: 'ACTIVE')),
                _chip('SUSPENDED', filter.status == 'SUSPENDED', () => _set(status: 'SUSPENDED')),
                _chip('CANCELLED', filter.status == 'CANCELLED', () => _set(status: 'CANCELLED')),
                _chip('FREE', filter.plan == 'FREE', () => _set(plan: filter.plan == 'FREE' ? '' : 'FREE')),
                _chip('PRO', filter.plan == 'PRO', () => _set(plan: filter.plan == 'PRO' ? '' : 'PRO')),
              ],
            ),
          ),
          Expanded(
            child: async.when(
              loading: () => const FsLoading(),
              error: (e, _) => FsErrorState(
                message: problemOf(e).detail,
                onRetry: () => ref.invalidate(tenantsProvider),
              ),
              data: (tenants) {
                if (tenants.isEmpty) {
                  return const FsEmptyState(title: 'No tenants match', message: 'Try another search or create a gym.');
                }
                return ListView.separated(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 88),
                  itemCount: tenants.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemBuilder: (context, i) {
                    final t = tenants[i];
                    final used = t.memberCount ?? 0;
                    final cap = t.maxMembers ?? 0;
                    return FsEnter(
                      delay: Duration(milliseconds: 40 * (i > 8 ? 8 : i)),
                      child: FsCard(
                        onTap: () => context.push('/tenants/${t.id}'),
                        child: ListTile(
                          contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                          leading: _TenantListLogo(tenant: t),
                          title: Text(
                            t.displayName?.isNotEmpty == true ? t.displayName! : t.name,
                            style: const TextStyle(fontWeight: FontWeight.w700),
                          ),
                          subtitle: Text(
                            [
                              t.slug,
                              t.planCode ?? '',
                              t.billingStatus ?? '',
                              '$used/$cap members',
                              if (t.lastLoginAt != null) 'login ${t.lastLoginAt!.split('T').first}',
                            ].where((e) => e.isNotEmpty).join(' · '),
                          ),
                          isThreeLine: true,
                          trailing: FsStatusChip(
                            label: t.status,
                            tone: t.status == 'ACTIVE' || t.status == 'ONBOARDING'
                                ? FsTone.success
                                : t.status == 'CANCELLED'
                                    ? FsTone.danger
                                    : FsTone.warning,
                          ),
                        ),
                      ),
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  void _set({String? status, String? plan}) {
    final cur = ref.read(tenantFilterProvider);
    ref.read(tenantFilterProvider.notifier).state = (
      q: cur.q,
      status: status ?? cur.status,
      plan: plan ?? cur.plan,
      type: cur.type,
    );
  }

  Widget _chip(String label, bool selected, VoidCallback onTap) {
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: FilterChip(label: Text(label), selected: selected, onSelected: (_) => onTap()),
    );
  }
}

class _TenantListLogo extends StatelessWidget {
  const _TenantListLogo({required this.tenant});

  final TenantSummary tenant;

  @override
  Widget build(BuildContext context) {
    final bytes = DueMateLogo.decodeBytes(tenant.logoBase64);
    if (bytes == null) {
      return const CircleAvatar(child: Icon(Icons.storefront_outlined));
    }
    return CircleAvatar(
      backgroundColor: const Color(0xFF0B0B0B),
      backgroundImage: MemoryImage(bytes),
    );
  }
}

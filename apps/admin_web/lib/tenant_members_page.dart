import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class TenantMembersPage extends ConsumerWidget {
  const TenantMembersPage({super.key, required this.tenantId});

  final String tenantId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(_membersProvider(tenantId));
    return Scaffold(
      appBar: AppBar(title: const Text('Members (read only)')),
      body: async.when(
        loading: () => const FsLoading(),
        error: (e, _) => FsErrorState(message: problemOf(e).detail, onRetry: () => ref.invalidate(_membersProvider(tenantId))),
        data: (rows) {
          if (rows.isEmpty) {
            return const FsEmptyState(title: 'No members', message: 'This gym has no members yet.');
          }
          return ListView.separated(
            itemCount: rows.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (context, i) {
              final c = rows[i];
              return ListTile(
                title: Text(c.fullName),
                subtitle: Text([c.customerCode, c.phone, c.email, c.status].whereType<String>().where((e) => e.isNotEmpty).join(' · ')),
              );
            },
          );
        },
      ),
    );
  }
}

final _membersProvider = FutureProvider.autoDispose.family<List<Customer>, String>((ref, id) {
  return ref.watch(platformApiProvider).members(id);
});

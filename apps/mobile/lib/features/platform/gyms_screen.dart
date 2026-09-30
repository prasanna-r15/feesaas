import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final gymsProvider = FutureProvider.autoDispose<List<TenantSummary>>((ref) {
  return ref.watch(platformApiProvider).listTenants();
});

class GymsScreen extends ConsumerWidget {
  const GymsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(gymsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Gyms')),
      body: async.when(
        loading: () => const FsLoading(),
        error: (e, _) => FsErrorState(
          message: problemOf(e).detail,
          onRetry: () => ref.invalidate(gymsProvider),
        ),
        data: (rows) {
          if (rows.isEmpty) {
            return const FsEmptyState(title: 'No gyms yet', message: 'Create a tenant from the API or seeder.');
          }
          return ListView.separated(
            itemCount: rows.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (context, i) {
              final t = rows[i];
              return ListTile(
                title: Text(t.name),
                subtitle: Text(
                  '${t.planCode ?? 'FREE'} · ${t.maxMembers ?? 0} members / ${t.maxStaff ?? 0} staff · ${t.status}',
                ),
                trailing: PopupMenuButton<String>(
                  onSelected: (v) async {
                    try {
                      if (v == 'FREE' || v == 'PRO') {
                        await ref.read(platformApiProvider).setPlan(t.id, v);
                      } else if (v == 'suspend') {
                        await ref.read(platformApiProvider).setStatus(t.id, suspend: t.status != 'SUSPENDED');
                      }
                      ref.invalidate(gymsProvider);
                    } catch (e) {
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(problemOf(e).detail)));
                      }
                    }
                  },
                  itemBuilder: (_) => [
                    const PopupMenuItem(value: 'FREE', child: Text('Set FREE (50 members)')),
                    const PopupMenuItem(value: 'PRO', child: Text('Set PRO (2000 members)')),
                    PopupMenuItem(
                      value: 'suspend',
                      child: Text(t.status == 'SUSPENDED' ? 'Activate' : 'Suspend'),
                    ),
                  ],
                ),
              );
            },
          );
        },
      ),
    );
  }
}

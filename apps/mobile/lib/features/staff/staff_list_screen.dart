import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

final staffListProvider = FutureProvider.autoDispose((ref) {
  return ref.watch(staffApiProvider).list();
});

class StaffListScreen extends ConsumerWidget {
  const StaffListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(staffListProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Staff')),
      floatingActionButton: FloatingActionButton(
        onPressed: () => context.push('/staff/new'),
        child: const Icon(Icons.add),
      ),
      body: async.when(
        loading: () => const FsLoading(),
        error: (e, _) => FsErrorState(message: problemOf(e).detail, onRetry: () => ref.invalidate(staffListProvider)),
        data: (staff) {
          if (staff.isEmpty) {
            return const FsEmptyState(title: 'No staff yet', message: 'Add a teammate and pick what they can do.');
          }
          return ListView.separated(
            itemCount: staff.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (context, i) {
              final person = staff[i];
              return ListTile(
                title: Text(person.fullName),
                subtitle: Text(person.email ?? person.phone ?? person.status),
                trailing: FsStatusChip(
                  label: person.status,
                  tone: person.status == 'ACTIVE' ? FsTone.success : FsTone.neutral,
                ),
                onLongPress: () async {
                  await ref.read(staffApiProvider).delete(person.id);
                  ref.invalidate(staffListProvider);
                },
              );
            },
          );
        },
      ),
    );
  }
}

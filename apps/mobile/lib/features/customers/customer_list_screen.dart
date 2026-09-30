import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/features/catalog/catalog_screens.dart';
import 'package:feesaas_mobile/util/files.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

final customerSearchProvider = StateProvider.autoDispose<String>((ref) => '');
final customerBranchFilterProvider = StateProvider.autoDispose<String?>((ref) => null);

final customerListProvider = FutureProvider.autoDispose<List<Customer>>((ref) {
  ref.watch(workspaceClockProvider);
  final q = ref.watch(customerSearchProvider);
  final branchId = ref.watch(customerBranchFilterProvider);
  return ref.watch(customerApiProvider).list(q: q, branchId: branchId);
});

class CustomerListScreen extends ConsumerWidget {
  const CustomerListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(tenantConfigProvider);
    final title = config?.label('customer.plural', 'Customers') ?? 'Customers';
    final singular = config?.label('customer.singular', 'Customer') ?? 'Customer';
    final async = ref.watch(customerListProvider);
    return Scaffold(
      appBar: AppBar(
        leading: const Padding(padding: EdgeInsets.all(6), child: DueMateLogo(height: 36)),
        title: Text(title),
        actions: [
          PermissionGate(
            permission: 'imports.run',
            child: IconButton(
              tooltip: 'Export Excel',
              onPressed: () async {
                try {
                  final bytes = await ref.read(customerApiProvider).exportXlsx();
                  final path = await saveBytes('duemate-members.xlsx', bytes);
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Downloaded $path')));
                  }
                } catch (e) {
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(problemOf(e).detail)));
                  }
                }
              },
              icon: const Icon(Icons.download_outlined),
            ),
          ),
          PermissionGate(
            permission: 'imports.run',
            child: IconButton(
              tooltip: 'Import CSV',
              onPressed: () async {
                await context.push('/more/import');
                ref.invalidate(customerListProvider);
              },
              icon: const Icon(Icons.file_upload_outlined),
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          await context.push('/customers/new');
          ref.invalidate(customerListProvider);
        },
        icon: const Icon(Icons.add),
        label: Text('Add $singular'),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
            child: TextField(
              decoration: InputDecoration(
                prefixIcon: const Icon(Icons.search),
                hintText: 'Search $title',
              ),
              onChanged: (v) => ref.read(customerSearchProvider.notifier).state = v.trim(),
            ),
          ),
          BranchFilterBar(
            value: ref.watch(customerBranchFilterProvider),
            onChanged: (v) => ref.read(customerBranchFilterProvider.notifier).state = v,
          ),
          Expanded(
            child: async.when(
              loading: () => const FsLoading(),
              error: (e, _) => FsErrorState(
                message: problemOf(e).detail,
                onRetry: () => ref.invalidate(customerListProvider),
              ),
              data: (rows) {
                if (rows.isEmpty) {
                  return FsEmptyState(
                    title: 'No $title yet',
                    message: 'Add a $singular to start collecting fees.',
                  );
                }
                return ListView.separated(
                  padding: const EdgeInsets.fromLTRB(16, 4, 16, 88),
                  itemCount: rows.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemBuilder: (context, i) {
                    final person = rows[i];
                    return FsEnter(
                      delay: Duration(milliseconds: 40 * (i > 8 ? 8 : i)),
                      child: FsCard(
                        onTap: () async {
                          await context.push('/customers/${person.id}');
                          ref.invalidate(customerListProvider);
                        },
                        child: ListTile(
                          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                          title: Text(person.fullName, style: const TextStyle(fontWeight: FontWeight.w700)),
                          subtitle: Text(
                            [
                              person.customerCode,
                              if (person.branchName != null) person.branchName!,
                              if (person.feePlanName != null) person.feePlanName!,
                              person.phone ?? person.email ?? '',
                              if (person.dueDate != null) 'Due ${person.dueDate}',
                            ].where((s) => s.isNotEmpty).join(' · '),
                          ),
                          trailing: FsStatusChip(
                            label: person.isOverdue ? 'Overdue' : person.status,
                            tone: person.isOverdue
                                ? FsTone.danger
                                : person.status == 'ACTIVE'
                                    ? FsTone.success
                                    : FsTone.neutral,
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
}

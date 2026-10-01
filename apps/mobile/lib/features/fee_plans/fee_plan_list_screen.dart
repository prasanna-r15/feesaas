import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

final feePlanListProvider = FutureProvider.autoDispose<List<FeePlan>>((ref) {
  ref.watch(workspaceClockProvider);
  return ref.watch(feeApiProvider).listPlans();
});

class FeePlanListScreen extends ConsumerWidget {
  const FeePlanListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(feePlanListProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Fee plans')),
      floatingActionButton: FloatingActionButton(
        onPressed: () async {
          await context.push('/fee-plans/new');
          ref.invalidate(feePlanListProvider);
        },
        child: const Icon(Icons.add),
      ),
      body: async.when(
        loading: () => const FsLoading(),
        error: (e, _) => FsErrorState(
          message: problemOf(e).detail,
          onRetry: () => ref.invalidate(feePlanListProvider),
        ),
        data: (plans) {
          if (plans.isEmpty) {
            return const FsEmptyState(
              title: 'No fee plans yet',
              message: 'Add a plan when you are ready to charge membership fees.',
            );
          }
          return ListView.separated(
            itemCount: plans.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (context, i) {
              final plan = plans[i];
              return ListTile(
                title: Text(plan.name),
                subtitle: Text('${plan.amountLabel} · ${plan.billingCycle}'),
                trailing: plan.isDefault ? const FsStatusChip(label: 'Default', tone: FsTone.success) : null,
                onTap: () async {
                  await context.push('/fee-plans/${plan.id}');
                  ref.invalidate(feePlanListProvider);
                },
              );
            },
          );
        },
      ),
    );
  }
}

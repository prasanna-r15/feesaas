import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/features/personal/personal_dashboard_screen.dart';
import 'package:feesaas_mobile/util/money.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final personalBudgetsProvider = FutureProvider<List<PersonalBudget>>((ref) {
  return ref.watch(personalApiProvider).budgets();
});

class PersonalBudgetsScreen extends ConsumerWidget {
  const PersonalBudgetsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(personalBudgetsProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Budgets'),
        actions: [
          IconButton(
            onPressed: () => _create(context, ref),
            icon: const Icon(Icons.add),
          ),
        ],
      ),
      body: async.when(
        loading: () => const FsLoading(),
        error: (e, _) => FsErrorState(message: problemOf(e).detail, onRetry: () => ref.invalidate(personalBudgetsProvider)),
        data: (items) {
          if (items.isEmpty) {
            return FsEmptyState(
              title: 'Create your first monthly budget.',
              action: FilledButton(onPressed: () => _create(context, ref), child: const Text('Add budget')),
            );
          }
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              for (final b in items)
                FsCard(
                  child: ListTile(
                    title: Text(b.categoryName),
                    subtitle: Text(b.percent >= 1
                        ? (b.percent > 1 ? 'Over budget' : 'Budget reached')
                        : (b.percent >= 0.8 ? 'Approaching limit' : '${(b.percent * 100).round()}% used')),
                    trailing: Text('${rupees(b.spentMinor)} / ${rupees(b.limitMinor)}'),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }

  Future<void> _create(BuildContext context, WidgetRef ref) async {
    final cats = await ref.read(personalApiProvider).categories();
    if (!context.mounted) return;
    String? cat = cats.isEmpty ? null : cats.first.id;
    final amount = TextEditingController();
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Monthly budget'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            DropdownButtonFormField<String>(
              value: cat,
              items: [for (final c in cats) DropdownMenuItem(value: c.id, child: Text(c.name))],
              onChanged: (v) => cat = v,
            ),
            TextField(controller: amount, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Limit (₹)')),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(
            onPressed: () async {
              final rupee = double.tryParse(amount.text.trim());
              if (cat == null || rupee == null) return;
              await ref.read(personalApiProvider).saveBudget(categoryId: cat!, limitMinor: (rupee * 100).round());
              ref.invalidate(personalBudgetsProvider);
              ref.invalidate(personalSummaryProvider);
              if (ctx.mounted) Navigator.pop(ctx);
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }
}

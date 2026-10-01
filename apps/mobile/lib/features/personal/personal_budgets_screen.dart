import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/features/personal/personal_dashboard_screen.dart';
import 'package:feesaas_mobile/util/money.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final personalBudgetsProvider = FutureProvider<List<PersonalBudget>>((ref) {
  return ref.watch(personalApiProvider).budgets(month: localYearMonth());
});

class PersonalBudgetsScreen extends ConsumerWidget {
  const PersonalBudgetsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(personalBudgetsProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Budgets'),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _edit(context, ref, null),
        child: const Icon(Icons.add),
      ),
      body: async.when(
        loading: () => const FsLoading(),
        error: (e, _) => FsErrorState(message: problemOf(e).detail, onRetry: () => ref.invalidate(personalBudgetsProvider)),
        data: (items) {
          if (items.isEmpty) {
            return FsEmptyState(
              title: 'Create your first monthly budget.',
              action: FilledButton(onPressed: () => _edit(context, ref, null), child: const Text('Add budget')),
            );
          }
          return ListView(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
            children: [
              for (final b in items)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: FsCard(
                    onTap: () => _edit(context, ref, b),
                    child: ListTile(
                      title: Text(b.categoryName),
                      subtitle: Text(b.percent >= 1
                          ? (b.percent > 1 ? 'Over budget' : 'Budget reached')
                          : (b.percent >= 0.8 ? 'Approaching limit' : '${(b.percent * 100).round()}% used')),
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text('${rupees(b.spentMinor)} / ${rupees(b.limitMinor)}'),
                          IconButton(
                            tooltip: 'Delete',
                            onPressed: () => _delete(context, ref, b),
                            icon: const Icon(Icons.delete_outline),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }

  Future<void> _delete(BuildContext context, WidgetRef ref, PersonalBudget b) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete budget'),
        content: Text('Remove the ${b.categoryName} budget for this month?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Delete')),
        ],
      ),
    );
    if (ok != true) return;
    await ref.read(personalApiProvider).deleteBudget(b.id);
    ref.invalidate(personalBudgetsProvider);
    ref.invalidate(personalSummaryProvider);
  }

  Future<void> _edit(BuildContext context, WidgetRef ref, PersonalBudget? existing) async {
    final cats = await ref.read(personalApiProvider).categories();
    if (!context.mounted) return;
    String? cat = existing?.categoryId ?? (cats.isEmpty ? null : cats.first.id);
    final amount = TextEditingController(
      text: existing == null ? '' : (existing.limitMinor / 100).toString(),
    );
    await showFsSheet<void>(
      context: context,
      builder: (ctx) => FsSheetForm(
        title: existing == null ? 'Monthly budget' : 'Edit budget',
        body: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            DropdownButtonFormField<String>(
              value: cats.any((c) => c.id == cat) ? cat : null,
              items: [for (final c in cats) DropdownMenuItem(value: c.id, child: Text(c.name))],
              onChanged: (v) => cat = v,
            ),
            TextField(
              controller: amount,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: const InputDecoration(labelText: 'Limit (₹)'),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(
            onPressed: () async {
              final rupee = parseRupees(amount.text);
              if (cat == null || rupee == null || rupee <= 0) return;
              final api = ref.read(personalApiProvider);
              final minor = (rupee * 100).round();
              if (existing == null) {
                await api.saveBudget(categoryId: cat!, limitMinor: minor, yearMonth: localYearMonth());
              } else {
                await api.updateBudget(existing.id, categoryId: cat!, limitMinor: minor);
              }
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

import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/features/auth/workspace_switcher.dart';
import 'package:feesaas_mobile/features/personal/add_expense_sheet.dart';
import 'package:feesaas_mobile/features/personal/add_income_sheet.dart';
import 'package:feesaas_mobile/util/money.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final personalSummaryProvider = FutureProvider.autoDispose<PersonalSummary>((ref) {
  ref.watch(workspaceClockProvider);
  return ref.watch(personalApiProvider).summary(month: localYearMonth());
});

class PersonalDashboardScreen extends ConsumerWidget {
  const PersonalDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(tenantConfigProvider);
    final async = ref.watch(personalSummaryProvider);
    final hour = DateTime.now().hour;
    final hello = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
    return Scaffold(
      appBar: AppBar(
        title: Text('$hello, ${config?.user.fullName.split(' ').first ?? ''}'),
        actions: [
          const WorkspaceSwitcher(),
          IconButton(
            tooltip: 'Add income',
            onPressed: () async {
              await showIncomeSheet(context, ref);
              ref.invalidate(personalSummaryProvider);
            },
            icon: const Icon(Icons.south_west),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          await showAddExpenseSheet(context, ref);
          ref.invalidate(personalSummaryProvider);
        },
        icon: const Icon(Icons.add),
        label: const Text('Add expense'),
      ),
      body: async.when(
        loading: () => const FsLoading(),
        error: (e, _) => FsErrorState(message: problemOf(e).detail, onRetry: () => ref.invalidate(personalSummaryProvider)),
        data: (s) {
          return RefreshIndicator(
            onRefresh: () async => ref.invalidate(personalSummaryProvider),
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
              children: [
                FsCard(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Row(
                      children: [
                        _kpi('Income', rupees(s.incomeMinor), context),
                        _kpi('Expenses', rupees(s.expenseMinor), context),
                        _kpi('Balance', rupees(s.savedMinor), context),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Text('This month', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
                const SizedBox(height: 8),
                if (s.byCategory.isEmpty)
                  FsEmptyState(
                    title: 'No expenses yet.',
                    message: 'Add your first expense in a few seconds.',
                    action: FilledButton(
                      onPressed: () => showAddExpenseSheet(context, ref),
                      child: const Text('Add your first expense'),
                    ),
                  )
                else
                  FsCard(
                    child: Column(
                      children: [
                        for (final c in s.byCategory)
                          ListTile(title: Text(c.name), trailing: Text(rupees(c.spentMinor), style: const TextStyle(fontWeight: FontWeight.w700))),
                      ],
                    ),
                  ),
                const SizedBox(height: 16),
                Text('Budget usage', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
                const SizedBox(height: 8),
                if (s.budgets.isEmpty)
                  const FsEmptyState(title: 'Create your first monthly budget.', message: 'Set a limit per category.')
                else
                  FsCard(
                    child: Column(
                      children: [
                        for (final b in s.budgets)
                          ListTile(
                            title: Text(b.categoryName),
                            subtitle: LinearProgressIndicator(value: b.percent.clamp(0, 1.5) / 1.5 > 1 ? 1 : b.percent.clamp(0, 1)),
                            trailing: Text('${rupees(b.spentMinor)} / ${rupees(b.limitMinor)}'),
                          ),
                      ],
                    ),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _kpi(String label, String value, BuildContext context) {
    return Expanded(
      child: Column(
        children: [
          Text(label, style: Theme.of(context).textTheme.labelMedium),
          const SizedBox(height: 6),
          Text(value, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
        ],
      ),
    );
  }
}

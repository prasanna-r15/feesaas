import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/features/personal/add_expense_sheet.dart';
import 'package:feesaas_mobile/features/personal/personal_dashboard_screen.dart';
import 'package:feesaas_mobile/util/money.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final personalExpensesProvider = FutureProvider<List<PersonalExpense>>((ref) {
  return ref.watch(personalApiProvider).expenses();
});

class PersonalExpensesScreen extends ConsumerWidget {
  const PersonalExpensesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(personalExpensesProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Expenses')),
      floatingActionButton: FloatingActionButton(
        onPressed: () async {
          await showAddExpenseSheet(context, ref);
          ref.invalidate(personalExpensesProvider);
          ref.invalidate(personalSummaryProvider);
        },
        child: const Icon(Icons.add),
      ),
      body: async.when(
        loading: () => const FsLoading(),
        error: (e, _) => FsErrorState(message: problemOf(e).detail, onRetry: () => ref.invalidate(personalExpensesProvider)),
        data: (items) {
          if (items.isEmpty) {
            return FsEmptyState(
              title: 'No expenses yet.',
              action: FilledButton(
                onPressed: () => showAddExpenseSheet(context, ref),
                child: const Text('Add your first expense'),
              ),
            );
          }
          return ListView.separated(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
            itemCount: items.length,
            separatorBuilder: (_, __) => const SizedBox(height: 8),
            itemBuilder: (context, i) {
              final e = items[i];
              return FsCard(
                child: ListTile(
                  title: Text(e.categoryName),
                  subtitle: Text(e.description?.isNotEmpty == true ? e.description! : e.occurredOn),
                  trailing: Text(rupees(e.amountMinor), style: const TextStyle(fontWeight: FontWeight.w800)),
                ),
              );
            },
          );
        },
      ),
    );
  }
}

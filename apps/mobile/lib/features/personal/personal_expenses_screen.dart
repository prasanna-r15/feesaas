import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/features/personal/add_expense_sheet.dart';
import 'package:feesaas_mobile/features/personal/add_income_sheet.dart';
import 'package:feesaas_mobile/features/personal/personal_dashboard_screen.dart';
import 'package:feesaas_mobile/util/money.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final personalExpensesProvider = FutureProvider<List<PersonalExpense>>((ref) {
  return ref.watch(personalApiProvider).expenses(month: localYearMonth());
});

final personalIncomeProvider = FutureProvider<List<PersonalIncome>>((ref) {
  return ref.watch(personalApiProvider).income(month: localYearMonth());
});

class PersonalExpensesScreen extends ConsumerStatefulWidget {
  const PersonalExpensesScreen({super.key});

  @override
  ConsumerState<PersonalExpensesScreen> createState() => _PersonalExpensesScreenState();
}

class _PersonalExpensesScreenState extends ConsumerState<PersonalExpensesScreen> {
  var _income = false;

  @override
  Widget build(BuildContext context) {
    final expenses = ref.watch(personalExpensesProvider);
    final income = ref.watch(personalIncomeProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Money'),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(52),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            child: SegmentedButton<bool>(
              segments: const [
                ButtonSegment(value: false, label: Text('Expenses'), icon: Icon(Icons.receipt_long_outlined)),
                ButtonSegment(value: true, label: Text('Income'), icon: Icon(Icons.south_west)),
              ],
              selected: {_income},
              onSelectionChanged: (v) => setState(() => _income = v.first),
            ),
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () async {
          if (_income) {
            await showIncomeSheet(context, ref);
            ref.invalidate(personalIncomeProvider);
          } else {
            await showAddExpenseSheet(context, ref);
            ref.invalidate(personalExpensesProvider);
          }
          ref.invalidate(personalSummaryProvider);
        },
        child: const Icon(Icons.add),
      ),
      body: _income ? _incomeList(income) : _expenseList(expenses),
    );
  }

  Widget _expenseList(AsyncValue<List<PersonalExpense>> async) {
    return async.when(
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
              onTap: () async {
                await showAddExpenseSheet(context, ref, existing: e);
                ref.invalidate(personalExpensesProvider);
                ref.invalidate(personalSummaryProvider);
              },
              child: ListTile(
                title: Text(e.categoryName),
                subtitle: Text(e.description?.isNotEmpty == true ? e.description! : e.occurredOn),
                trailing: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(rupees(e.amountMinor), style: const TextStyle(fontWeight: FontWeight.w800)),
                    IconButton(
                      tooltip: 'Delete',
                      onPressed: () => _deleteExpense(e),
                      icon: const Icon(Icons.delete_outline),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  Widget _incomeList(AsyncValue<List<PersonalIncome>> async) {
    return async.when(
      loading: () => const FsLoading(),
      error: (e, _) => FsErrorState(message: problemOf(e).detail, onRetry: () => ref.invalidate(personalIncomeProvider)),
      data: (items) {
        if (items.isEmpty) {
          return FsEmptyState(
            title: 'No income yet.',
            action: FilledButton(
              onPressed: () => showIncomeSheet(context, ref),
              child: const Text('Add income'),
            ),
          );
        }
        return ListView.separated(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
          itemCount: items.length,
          separatorBuilder: (_, __) => const SizedBox(height: 8),
          itemBuilder: (context, i) {
            final row = items[i];
            return FsCard(
              onTap: () async {
                await showIncomeSheet(context, ref, existing: row);
                ref.invalidate(personalIncomeProvider);
                ref.invalidate(personalSummaryProvider);
              },
              child: ListTile(
                title: Text(row.source),
                subtitle: Text(row.description?.isNotEmpty == true ? row.description! : row.occurredOn),
                trailing: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(rupees(row.amountMinor), style: const TextStyle(fontWeight: FontWeight.w800)),
                    IconButton(
                      tooltip: 'Delete',
                      onPressed: () => _deleteIncome(row),
                      icon: const Icon(Icons.delete_outline),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  Future<void> _deleteExpense(PersonalExpense e) async {
    final ok = await _confirm('Delete this expense?');
    if (!ok) return;
    try {
      await ref.read(personalApiProvider).deleteExpense(e.id);
      ref.invalidate(personalExpensesProvider);
      ref.invalidate(personalSummaryProvider);
    } catch (err) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(problemOf(err).detail)));
      }
    }
  }

  Future<void> _deleteIncome(PersonalIncome row) async {
    final ok = await _confirm('Delete this income?');
    if (!ok) return;
    try {
      await ref.read(personalApiProvider).deleteIncome(row.id);
      ref.invalidate(personalIncomeProvider);
      ref.invalidate(personalSummaryProvider);
    } catch (err) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(problemOf(err).detail)));
      }
    }
  }

  Future<bool> _confirm(String message) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete'),
        content: Text(message),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Delete')),
        ],
      ),
    );
    return result == true;
  }
}

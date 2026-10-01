import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/util/money.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

Future<void> showAddExpenseSheet(BuildContext context, WidgetRef ref, {PersonalExpense? existing}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (ctx) => _AddExpenseSheet(existing: existing),
  );
}

class _AddExpenseSheet extends ConsumerStatefulWidget {
  const _AddExpenseSheet({this.existing});

  final PersonalExpense? existing;

  @override
  ConsumerState<_AddExpenseSheet> createState() => _AddExpenseSheetState();
}

class _AddExpenseSheetState extends ConsumerState<_AddExpenseSheet> {
  late final TextEditingController _amount;
  late final TextEditingController _note;
  String? _categoryId;
  var _busy = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    final existing = widget.existing;
    _amount = TextEditingController(
      text: existing == null ? '' : (existing.amountMinor / 100).toString(),
    );
    _note = TextEditingController(text: existing?.description ?? '');
    _categoryId = existing?.categoryId;
  }

  @override
  void dispose() {
    _amount.dispose();
    _note.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final editing = widget.existing != null;
    return Padding(
      padding: EdgeInsets.only(left: 20, right: 20, bottom: MediaQuery.viewInsetsOf(context).bottom + 24, top: 8),
      child: FutureBuilder(
        future: ref.read(personalApiProvider).categories(),
        builder: (context, snap) {
          final cats = snap.data ?? const <PersonalCategory>[];
          _categoryId ??= cats.where((c) => c.active).isEmpty ? null : cats.where((c) => c.active).first.id;
          return Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(editing ? 'Edit expense' : 'Add expense', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)),
              const SizedBox(height: 16),
              TextField(
                controller: _amount,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: const InputDecoration(labelText: 'Amount (₹)', prefixIcon: Icon(Icons.currency_rupee)),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                value: cats.any((c) => c.id == _categoryId) ? _categoryId : null,
                items: [
                  for (final c in cats.where((c) => c.active))
                    DropdownMenuItem(value: c.id, child: Text(c.name)),
                ],
                onChanged: (v) => setState(() => _categoryId = v),
                decoration: const InputDecoration(labelText: 'Category'),
              ),
              const SizedBox(height: 12),
              TextField(controller: _note, decoration: const InputDecoration(labelText: 'Note (optional)')),
              if (_error != null) ...[
                const SizedBox(height: 8),
                Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
              ],
              const SizedBox(height: 16),
              FilledButton(
                onPressed: _busy ? null : _save,
                child: _busy
                    ? const SizedBox(height: 22, width: 22, child: CircularProgressIndicator(strokeWidth: 2))
                    : Text(editing ? 'Save changes' : 'Save expense'),
              ),
            ],
          );
        },
      ),
    );
  }

  Future<void> _save() async {
    final rupee = parseRupees(_amount.text);
    if (rupee == null || rupee <= 0 || _categoryId == null) {
      setState(() => _error = 'Enter an amount and category.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final api = ref.read(personalApiProvider);
      final existing = widget.existing;
      if (existing == null) {
        await api.addExpense(
          amountMinor: (rupee * 100).round(),
          categoryId: _categoryId!,
          description: _note.text.trim().isEmpty ? null : _note.text.trim(),
          occurredOn: localIsoDate(),
          method: 'UPI',
        );
      } else {
        await api.updateExpense(
          existing.id,
          amountMinor: (rupee * 100).round(),
          categoryId: _categoryId!,
          description: _note.text.trim().isEmpty ? null : _note.text.trim(),
          occurredOn: existing.occurredOn,
          method: existing.method ?? 'UPI',
        );
      }
      if (mounted) Navigator.pop(context);
    } catch (e) {
      setState(() {
        _busy = false;
        _error = problemOf(e).detail;
      });
    }
  }
}

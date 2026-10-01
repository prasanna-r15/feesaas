import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/util/money.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

Future<void> showIncomeSheet(BuildContext context, WidgetRef ref, {PersonalIncome? existing}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (ctx) => _IncomeSheet(existing: existing),
  );
}

class _IncomeSheet extends ConsumerStatefulWidget {
  const _IncomeSheet({this.existing});

  final PersonalIncome? existing;

  @override
  ConsumerState<_IncomeSheet> createState() => _IncomeSheetState();
}

class _IncomeSheetState extends ConsumerState<_IncomeSheet> {
  late final TextEditingController _amount;
  late final TextEditingController _source;
  var _busy = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    final existing = widget.existing;
    _amount = TextEditingController(
      text: existing == null ? '' : (existing.amountMinor / 100).toString(),
    );
    _source = TextEditingController(text: existing?.source ?? 'Salary');
  }

  @override
  void dispose() {
    _amount.dispose();
    _source.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final editing = widget.existing != null;
    return Padding(
      padding: EdgeInsets.only(left: 20, right: 20, bottom: MediaQuery.viewInsetsOf(context).bottom + 24, top: 8),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(editing ? 'Edit income' : 'Add income', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)),
          const SizedBox(height: 16),
          TextField(
            controller: _amount,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(labelText: 'Amount (₹)', prefixIcon: Icon(Icons.currency_rupee)),
          ),
          const SizedBox(height: 12),
          TextField(controller: _source, decoration: const InputDecoration(labelText: 'Source')),
          if (_error != null) ...[
            const SizedBox(height: 8),
            Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ],
          const SizedBox(height: 16),
          FilledButton(
            onPressed: _busy ? null : _save,
            child: _busy
                ? const SizedBox(height: 22, width: 22, child: CircularProgressIndicator(strokeWidth: 2))
                : Text(editing ? 'Save changes' : 'Save income'),
          ),
        ],
      ),
    );
  }

  Future<void> _save() async {
    final rupee = parseRupees(_amount.text);
    if (rupee == null || rupee <= 0) {
      setState(() => _error = 'Enter an amount.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final api = ref.read(personalApiProvider);
      final source = _source.text.trim().isEmpty ? 'Other' : _source.text.trim();
      final existing = widget.existing;
      if (existing == null) {
        await api.addIncome(
          amountMinor: (rupee * 100).round(),
          source: source,
          occurredOn: localIsoDate(),
        );
      } else {
        await api.updateIncome(
          existing.id,
          amountMinor: (rupee * 100).round(),
          source: source,
          description: existing.description,
          occurredOn: existing.occurredOn,
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

import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class FeePlanFormScreen extends ConsumerStatefulWidget {
  const FeePlanFormScreen({super.key, this.planId});

  final String? planId;

  @override
  ConsumerState<FeePlanFormScreen> createState() => _FeePlanFormScreenState();
}

class _FeePlanFormScreenState extends ConsumerState<FeePlanFormScreen> {
  final _name = TextEditingController();
  final _amount = TextEditingController();
  var _cycle = 'MONTHLY';
  var _default = false;
  var _busy = false;
  var _loading = false;
  String? _error;

  bool get _editing => widget.planId != null;

  @override
  void initState() {
    super.initState();
    if (_editing) {
      _loading = true;
      _load();
    }
  }

  Future<void> _load() async {
    try {
      final plan = await ref.read(feeApiProvider).getPlan(widget.planId!);
      if (!mounted) {
        return;
      }
      _name.text = plan.name;
      _amount.text = (plan.amountMinor / 100).toStringAsFixed(plan.amountMinor % 100 == 0 ? 0 : 2);
      _cycle = plan.billingCycle;
      _default = plan.isDefault;
    } catch (e) {
      _error = problemOf(e).detail;
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  void dispose() {
    _name.dispose();
    _amount.dispose();
    super.dispose();
  }

  int? _minorFromRupees() {
    final raw = _amount.text.trim().replaceAll(',', '');
    final value = double.tryParse(raw);
    if (value == null || value <= 0) {
      return null;
    }
    return (value * 100).round();
  }

  Future<void> _save() async {
    final minor = _minorFromRupees();
    if (_name.text.trim().isEmpty || minor == null) {
      setState(() => _error = 'Name and a positive amount in rupees are required.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final api = ref.read(feeApiProvider);
      if (_editing) {
        await api.patchPlan(
          widget.planId!,
          name: _name.text.trim(),
          amountMinor: minor,
          billingCycle: _cycle,
          isDefault: _default,
        );
      } else {
        await api.createPlan(
          name: _name.text.trim(),
          amountMinor: minor,
          billingCycle: _cycle,
          isDefault: _default,
        );
      }
      if (mounted) {
        context.pop();
      }
    } catch (e) {
      setState(() => _error = problemOf(e).detail);
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _delete() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete this plan?'),
        content: const Text('Members on this plan must be moved first.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Delete')),
        ],
      ),
    );
    if (ok != true || !mounted) {
      return;
    }
    setState(() => _busy = true);
    try {
      await ref.read(feeApiProvider).deletePlan(widget.planId!);
      if (mounted) {
        context.pop();
      }
    } catch (e) {
      setState(() => _error = problemOf(e).detail);
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return Scaffold(appBar: AppBar(title: const Text('Fee plan')), body: const FsLoading());
    }
    return Scaffold(
      appBar: AppBar(
        title: Text(_editing ? 'Edit plan' : 'Add plan'),
        actions: [
          if (_editing)
            IconButton(onPressed: _busy ? null : _delete, icon: const Icon(Icons.delete_outline)),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(
            controller: _name,
            textCapitalization: TextCapitalization.words,
            decoration: const InputDecoration(labelText: 'Name', hintText: 'General, Cardio, PT…'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _amount,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(labelText: 'Amount (₹)', hintText: '1500'),
          ),
          const SizedBox(height: 16),
          DropdownButtonFormField<String>(
            value: _cycle,
            decoration: const InputDecoration(labelText: 'Billing cycle'),
            items: const [
              DropdownMenuItem(value: 'WEEKLY', child: Text('Weekly')),
              DropdownMenuItem(value: 'MONTHLY', child: Text('Monthly')),
              DropdownMenuItem(value: 'QUARTERLY', child: Text('Quarterly')),
              DropdownMenuItem(value: 'HALF_YEARLY', child: Text('Half-yearly')),
              DropdownMenuItem(value: 'ANNUAL', child: Text('Annual')),
            ],
            onChanged: (v) {
              if (v != null) {
                setState(() => _cycle = v);
              }
            },
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Default plan for new members'),
            value: _default,
            onChanged: (v) => setState(() => _default = v),
          ),
          if (_error != null) ...[
            const SizedBox(height: 8),
            Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ],
          const SizedBox(height: 24),
          FilledButton(onPressed: _busy ? null : _save, child: Text(_editing ? 'Save' : 'Add')),
        ],
      ),
    );
  }
}

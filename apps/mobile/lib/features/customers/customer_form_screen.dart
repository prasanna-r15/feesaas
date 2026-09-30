import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/features/catalog/catalog_screens.dart';
import 'package:feesaas_mobile/features/fee_plans/fee_plan_list_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class CustomerFormScreen extends ConsumerStatefulWidget {
  const CustomerFormScreen({super.key, this.customerId});

  final String? customerId;

  @override
  ConsumerState<CustomerFormScreen> createState() => _CustomerFormScreenState();
}

class _CustomerFormScreenState extends ConsumerState<CustomerFormScreen> {
  final _name = TextEditingController();
  final _phone = TextEditingController();
  final _email = TextEditingController();
  final _notes = TextEditingController();
  var _status = 'ACTIVE';
  DateTime _dueDate = DateTime.now().add(const Duration(days: 30));
  var _busy = false;
  var _loading = false;
  String? _error;
  String? _code;
  String? _planId;
  String? _branchId;
  var _hasWhatsapp = true;

  bool get _editing => widget.customerId != null;

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
      final person = await ref.read(customerApiProvider).get(widget.customerId!);
      if (!mounted) {
        return;
      }
      _name.text = person.fullName;
      _phone.text = person.phone ?? '';
      _email.text = person.email ?? '';
      _notes.text = person.notes ?? '';
      _status = person.status;
      _code = person.customerCode;
      _planId = person.feePlanId;
      _branchId = person.branchId;
      _hasWhatsapp = person.hasWhatsapp;
      if (person.dueDate != null) {
        final parsed = DateTime.tryParse(person.dueDate!);
        if (parsed != null) {
          _dueDate = parsed;
        }
      }
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
    _phone.dispose();
    _email.dispose();
    _notes.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      var planId = _planId;
      if (planId == null) {
        final rows = await ref.read(feeApiProvider).listPlans();
        if (rows.isNotEmpty) {
          planId = rows.firstWhere((p) => p.isDefault, orElse: () => rows.first).id;
        }
      }
      final api = ref.read(customerApiProvider);
      if (_editing) {
        await api.patch(
          widget.customerId!,
          fullName: _name.text.trim(),
          phone: _phone.text.trim(),
          email: _email.text.trim(),
          status: _status,
          notes: _notes.text.trim(),
          dueDate: _isoDate(_dueDate),
          feePlanId: planId,
          hasWhatsapp: _hasWhatsapp,
          branchId: _branchId,
        );
      } else {
        await api.create(
          fullName: _name.text.trim(),
          phone: _phone.text.trim(),
          email: _email.text.trim(),
          notes: _notes.text.trim(),
          dueDate: _isoDate(_dueDate),
          feePlanId: planId,
          hasWhatsapp: _hasWhatsapp,
          branchId: _branchId,
        );
      }
      if (mounted) {
        tickWorkspace(ref);
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
        title: const Text('Remove this person?'),
        content: const Text('They will be hidden from lists. This can be reversed later in a following step.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Remove')),
        ],
      ),
    );
    if (ok != true || !mounted) {
      return;
    }
    setState(() => _busy = true);
    try {
      await ref.read(customerApiProvider).delete(widget.customerId!);
      if (mounted) {
        tickWorkspace(ref);
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
    final config = ref.watch(tenantConfigProvider);
    final singular = config?.label('customer.singular', 'Customer') ?? 'Customer';
    final plans = ref.watch(feePlanListProvider);
    if (_loading) {
      return Scaffold(appBar: AppBar(title: Text(singular)), body: const FsLoading());
    }
    return Scaffold(
      appBar: AppBar(
        title: Text(_editing ? (_code ?? singular) : 'Add $singular'),
        actions: [
          if (_editing)
            IconButton(
              onPressed: _busy ? null : _delete,
              icon: const Icon(Icons.delete_outline),
            ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(controller: _name, textCapitalization: TextCapitalization.words, decoration: const InputDecoration(labelText: 'Full name')),
          const SizedBox(height: 12),
          TextField(controller: _phone, keyboardType: TextInputType.phone, decoration: const InputDecoration(labelText: 'Phone')),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Has WhatsApp'),
            subtitle: const Text('Off = remind with a normal text message instead'),
            value: _hasWhatsapp,
            onChanged: (v) => setState(() => _hasWhatsapp = v),
          ),
          const SizedBox(height: 12),
          TextField(controller: _email, keyboardType: TextInputType.emailAddress, decoration: const InputDecoration(labelText: 'Email')),
          const SizedBox(height: 12),
          TextField(controller: _notes, maxLines: 3, decoration: const InputDecoration(labelText: 'Notes')),
          const SizedBox(height: 12),
          ModuleGate(
            module: 'BRANCHES',
            child: Consumer(
              builder: (context, ref, _) {
                final branches = ref.watch(branchListProvider);
                return branches.when(
                  loading: () => const SizedBox.shrink(),
                  error: (_, __) => const SizedBox.shrink(),
                  data: (rows) {
                    if (rows.isEmpty) {
                      return const Text('No locations yet. Add them under More → Locations.');
                    }
                    return LocationPickerField(
                      branches: rows,
                      value: rows.any((b) => b.id == _branchId) ? _branchId : null,
                      allLabel: 'Unassigned',
                      onChanged: (v) => setState(() => _branchId = v),
                    );
                  },
                );
              },
            ),
          ),
          const SizedBox(height: 12),
          plans.when(
            loading: () => const LinearProgressIndicator(),
            error: (e, _) => Text(problemOf(e).detail),
            data: (rows) {
              if (rows.isEmpty) {
                return const Text('Create a fee plan under More → Fee plans first.');
              }
              final ids = rows.map((p) => p.id).toSet();
              final value = ids.contains(_planId) ? _planId : rows.firstWhere((p) => p.isDefault, orElse: () => rows.first).id;
              return DropdownButtonFormField<String>(
                value: value,
                decoration: const InputDecoration(labelText: 'Fee plan'),
                items: [
                  for (final plan in rows)
                    DropdownMenuItem(
                      value: plan.id,
                      child: Text('${plan.name} · ${plan.amountLabel}'),
                    ),
                ],
                onChanged: (v) => setState(() => _planId = v),
              );
            },
          ),
          const SizedBox(height: 12),
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Due date'),
            subtitle: Text(_isoDate(_dueDate)),
            trailing: const Icon(Icons.calendar_month_outlined),
            onTap: _busy
                ? null
                : () async {
                    final picked = await showDatePicker(
                      context: context,
                      initialDate: _dueDate,
                      firstDate: DateTime(2020),
                      lastDate: DateTime(2100),
                    );
                    if (picked != null) {
                      setState(() => _dueDate = picked);
                    }
                  },
          ),
          if (_editing) ...[
            const SizedBox(height: 16),
            SegmentedButton<String>(
              segments: const [
                ButtonSegment(value: 'ACTIVE', label: Text('Active')),
                ButtonSegment(value: 'INACTIVE', label: Text('Inactive')),
              ],
              selected: {_status},
              onSelectionChanged: (s) => setState(() => _status = s.first),
            ),
          ],
          if (_error != null) ...[
            const SizedBox(height: 12),
            Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ],
          const SizedBox(height: 24),
          FilledButton(onPressed: _busy ? null : _save, child: Text(_editing ? 'Save' : 'Add')),
        ],
      ),
    );
  }
}

String _isoDate(DateTime date) {
  final y = date.year.toString().padLeft(4, '0');
  final m = date.month.toString().padLeft(2, '0');
  final d = date.day.toString().padLeft(2, '0');
  return '$y-$m-$d';
}

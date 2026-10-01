import 'package:feesaas_admin_web/admin_app_bar.dart';
import 'package:feesaas_admin_web/slug.dart';
import 'package:feesaas_admin_web/tenant_logo_field.dart';
import 'package:feesaas_admin_web/tenants_page.dart';
import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

final tenantDetailProvider = FutureProvider.autoDispose.family<TenantSummary, String>((ref, id) {
  return ref.watch(platformApiProvider).getTenant(id);
});

class EditTenantPage extends ConsumerStatefulWidget {
  const EditTenantPage({super.key, required this.tenantId});

  final String tenantId;

  @override
  ConsumerState<EditTenantPage> createState() => _EditTenantPageState();
}

class _EditTenantPageState extends ConsumerState<EditTenantPage> {
  final _name = TextEditingController();
  final _slug = TextEditingController();
  final _display = TextEditingController();
  final _accent = TextEditingController();
  final _grace = TextEditingController();
  final _maxMembers = TextEditingController();
  final _maxStaff = TextEditingController();
  final _phone = TextEditingController();
  final _whatsapp = TextEditingController();
  final _ownerName = TextEditingController();
  final _ownerEmail = TextEditingController();
  final _ownerPhone = TextEditingController();
  final _note = TextEditingController();
  final _invoicePeriod = TextEditingController();
  final _invoiceAmount = TextEditingController();
  var _loaded = false;
  var _busy = false;
  var _clearLogo = false;
  var _plan = 'FREE';
  var _status = 'ACTIVE';
  var _billing = 'OK';
  final _modules = <String>{};
  String? _error;
  String? _logo;
  String? _originalLogo;
  List<Map<String, dynamic>> _notes = [];
  List<Map<String, dynamic>> _invoices = [];
  List<Map<String, dynamic>> _audit = [];
  List<Map<String, dynamic>> _branches = [];
  List<Map<String, dynamic>> _addons = [];
  List<Map<String, dynamic>> _diets = [];
  List<Map<String, dynamic>> _feePlans = [];

  static const _core = ['CUSTOMERS', 'SETTINGS', 'NOTIFICATIONS', 'REPORTS'];
  static const _optional = [
    ('FEES', 'Membership fees'),
    ('PAYMENTS', 'Collections & receipts'),
    ('MEMBERSHIP', 'Membership tracking'),
    ('ATTENDANCE', 'Batches & attendance'),
    ('BRANCHES', 'Multiple locations'),
    ('ADDONS', 'Protein, diet packs & extras'),
    ('DIET_CHARTS', 'Diet charts on WhatsApp'),
  ];

  @override
  void dispose() {
    for (final c in [
      _name, _slug, _display, _accent, _grace, _maxMembers, _maxStaff, _phone, _whatsapp,
      _ownerName, _ownerEmail, _ownerPhone, _note, _invoicePeriod, _invoiceAmount,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    ref.listenManual(tenantDetailProvider(widget.tenantId), (previous, next) {
      next.whenData(_hydrate);
    }, fireImmediately: true);
    _reloadExtras();
    _reloadCatalog();
  }

  Future<void> _reloadExtras() async {
    final api = ref.read(platformApiProvider);
    try {
      final notes = await api.notes(widget.tenantId);
      final invoices = await api.invoices(widget.tenantId);
      final audit = await api.audit(widget.tenantId);
      if (mounted) {
        setState(() {
          _notes = notes;
          _invoices = invoices;
          _audit = audit;
        });
      }
    } catch (_) {}
  }

  Future<void> _reloadCatalog() async {
    final api = ref.read(platformApiProvider);
    try {
      final branches = await api.tenantBranches(widget.tenantId);
      final addons = await api.tenantAddons(widget.tenantId);
      final diets = await api.tenantDiets(widget.tenantId);
      final feePlans = await api.tenantFeePlans(widget.tenantId);
      if (mounted) {
        setState(() {
          _branches = branches;
          _addons = addons;
          _diets = diets;
          _feePlans = feePlans;
        });
      }
    } catch (_) {}
  }

  void _hydrate(TenantSummary t) {
    if (_loaded) {
      return;
    }
    _loaded = true;
    _name.text = t.name;
    _slug.text = t.slug;
    _display.text = t.displayName ?? t.name;
    _accent.text = t.accentColor ?? '#0F766E';
    _grace.text = '${t.graceDays ?? 7}';
    _maxMembers.text = t.customMaxMembers?.toString() ?? '';
    _maxStaff.text = t.customMaxStaff?.toString() ?? '';
    _phone.text = t.phone ?? '';
    _whatsapp.text = t.whatsappNumber ?? '';
    _ownerName.text = t.ownerFullName ?? '';
    _ownerEmail.text = t.ownerEmail ?? '';
    _ownerPhone.text = t.ownerPhone ?? '';
    setState(() {
      _plan = t.planCode ?? 'FREE';
      _status = t.status;
      _billing = t.billingStatus ?? 'OK';
      _logo = t.logoBase64;
      _originalLogo = t.logoBase64;
      _modules
        ..clear()
        ..addAll(t.modules.where((m) => _optional.any((o) => o.$1 == m)));
    });
  }

  Future<void> _save() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final api = ref.read(platformApiProvider);
      await api.patchTenant(
        widget.tenantId,
        name: _name.text.trim(),
        slug: slugify(_slug.text.isEmpty ? _name.text : _slug.text),
        logoBase64: (_clearLogo || _logo == _originalLogo) ? null : _logo,
        clearLogo: _clearLogo || (_originalLogo != null && _logo == null),
        displayName: _display.text.trim(),
        accentColor: _accent.text.trim(),
        graceDays: int.tryParse(_grace.text.trim()),
        billingStatus: _billing,
        customMaxMembers: int.tryParse(_maxMembers.text.trim()),
        customMaxStaff: int.tryParse(_maxStaff.text.trim()),
        clearCustomLimits: _maxMembers.text.trim().isEmpty && _maxStaff.text.trim().isEmpty,
        phone: _phone.text.trim(),
        whatsappNumber: _whatsapp.text.trim(),
      );
      await api.setPlan(widget.tenantId, _plan);
      await api.setModules(widget.tenantId, [..._core, ..._modules]);
      await api.updateOwner(
        widget.tenantId,
        fullName: _ownerName.text.trim(),
        email: _ownerEmail.text.trim(),
        phone: _ownerPhone.text.trim(),
      );
      if (mounted) {
        _originalLogo = _logo;
        _clearLogo = false;
        ref.invalidate(tenantsProvider);
        ref.invalidate(tenantDetailProvider(widget.tenantId));
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Saved')));
      }
    } catch (e) {
      setState(() => _error = problemOf(e).detail);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<bool> _confirm(String title, String expected) async {
    final controller = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: TextField(
          controller: controller,
          decoration: InputDecoration(labelText: 'Type "$expected" to confirm'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Back')),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text.trim() == expected),
            child: const Text('Confirm'),
          ),
        ],
      ),
    );
    return ok == true;
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(tenantDetailProvider(widget.tenantId));
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      backgroundColor: scheme.surfaceContainerLowest,
      appBar: AppBar(
        title: const Text('Tenant workspace'),
        actions: [
          const AdminChatAction(),
          TextButton(onPressed: () => context.push('/tenants/${widget.tenantId}/members'), child: const Text('Members')),
        ],
      ),
      body: async.when(
        loading: () => const FsLoading(),
        error: (e, _) => FsErrorState(
          message: problemOf(e).detail,
          onRetry: () => ref.invalidate(tenantDetailProvider(widget.tenantId)),
        ),
        data: (tenant) {
          return Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 880),
              child: ListView(
                padding: const EdgeInsets.fromLTRB(20, 16, 20, 48),
                children: [
                  _HeroCard(
                    name: tenant.name,
                    status: _status,
                    members: '${tenant.memberCount ?? 0}/${tenant.maxMembers ?? 0}',
                    staff: '${tenant.staffCount ?? 0}/${tenant.maxStaff ?? 0}',
                    lastLogin: formatLocalDateTime(tenant.lastLoginAt, dateOnly: false).isEmpty
                        ? 'never'
                        : formatLocalDateTime(tenant.lastLoginAt),
                    lastCollection: tenant.lastCollectionOn == null || tenant.lastCollectionOn!.isEmpty
                        ? 'none'
                        : formatLocalDateTime(tenant.lastCollectionOn, dateOnly: true),
                  ),
                  const SizedBox(height: 16),
                  _Section(
                    title: 'Business profile',
                    subtitle: 'What owners and members see on receipts and login.',
                    child: Column(
                      children: [
                        TextField(controller: _name, decoration: const InputDecoration(labelText: 'Business name')),
                        const SizedBox(height: 12),
                        TextField(controller: _display, decoration: const InputDecoration(labelText: 'Display name')),
                        const SizedBox(height: 12),
                        TextField(controller: _slug, decoration: const InputDecoration(labelText: 'URL slug')),
                        const SizedBox(height: 12),
                        TextField(controller: _accent, decoration: const InputDecoration(labelText: 'Accent color', helperText: '#0F766E')),
                        const SizedBox(height: 16),
                        TenantLogoField(
                          dataUri: _logo,
                          onChanged: (v) => setState(() {
                            _logo = v;
                            _clearLogo = v == null;
                          }),
                        ),
                      ],
                    ),
                  ),
                  _Section(
                    title: 'Plan & billing',
                    subtitle: 'Commercial terms for this tenant. Limits override the plan when set.',
                    child: Column(
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: DropdownButtonFormField(
                                value: _plan,
                                items: const [
                                  DropdownMenuItem(value: 'FREE', child: Text('FREE')),
                                  DropdownMenuItem(value: 'PRO', child: Text('PRO')),
                                ],
                                onChanged: (v) => setState(() => _plan = v ?? 'FREE'),
                                decoration: const InputDecoration(labelText: 'Plan'),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: DropdownButtonFormField(
                                value: _billing,
                                items: const [
                                  DropdownMenuItem(value: 'OK', child: Text('OK')),
                                  DropdownMenuItem(value: 'PAST_DUE', child: Text('Past due')),
                                  DropdownMenuItem(value: 'UNPAID', child: Text('Unpaid')),
                                ],
                                onChanged: (v) => setState(() => _billing = v ?? 'OK'),
                                decoration: const InputDecoration(labelText: 'Billing'),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        TextField(controller: _grace, decoration: const InputDecoration(labelText: 'Grace days after trial')),
                        const SizedBox(height: 12),
                        TextField(controller: _maxMembers, decoration: const InputDecoration(labelText: 'Custom max members')),
                        const SizedBox(height: 12),
                        TextField(controller: _maxStaff, decoration: const InputDecoration(labelText: 'Custom max staff')),
                      ],
                    ),
                  ),
                  _Section(
                    title: 'Products this tenant can sell',
                    subtitle: 'Core tools stay on. Turn on extras that match the business — gym, academy, or tuition.',
                    child: Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        for (final code in _core)
                          FilterChip(label: Text(code), selected: true, onSelected: null),
                        for (final item in _optional)
                          FilterChip(
                            label: Text(item.$2),
                            selected: _modules.contains(item.$1),
                            onSelected: (on) => setState(() => on ? _modules.add(item.$1) : _modules.remove(item.$1)),
                          ),
                      ],
                    ),
                  ),
                  _Section(
                    title: 'Owner & contact',
                    subtitle: 'Owner login and WhatsApp number used when staff message members.',
                    child: Column(
                      children: [
                        TextField(controller: _phone, decoration: const InputDecoration(labelText: 'Business phone')),
                        const SizedBox(height: 8),
                        TextField(controller: _whatsapp, decoration: const InputDecoration(labelText: 'WhatsApp')),
                        const SizedBox(height: 12),
                        TextField(controller: _ownerName, decoration: const InputDecoration(labelText: 'Owner name')),
                        const SizedBox(height: 8),
                        TextField(controller: _ownerEmail, decoration: const InputDecoration(labelText: 'Owner email')),
                        const SizedBox(height: 8),
                        TextField(controller: _ownerPhone, decoration: const InputDecoration(labelText: 'Owner phone')),
                        const SizedBox(height: 12),
                        OutlinedButton(
                          onPressed: _busy
                              ? null
                              : () async {
                                  final password = await ref.read(platformApiProvider).resetOwnerPassword(widget.tenantId);
                                  if (mounted) {
                                    await showDialog<void>(
                                      context: context,
                                      builder: (c) => AlertDialog(
                                        title: const Text('New owner password'),
                                        content: SelectableText(password),
                                        actions: [TextButton(onPressed: () => Navigator.pop(c), child: const Text('Done'))],
                                      ),
                                    );
                                  }
                                },
                          child: const Text('Reset owner password'),
                        ),
                      ],
                    ),
                  ),
                  _Section(
                    title: 'Locations',
                    subtitle: 'One tenant, many branches — Karamadai, Teachers Colony, and so on.',
                    child: _NamedList(
                      rows: _branches,
                      empty: 'No locations yet. Add the first branch for this tenant.',
                      onAdd: () => _promptAdd(
                        title: 'New location',
                        fields: const ['Name', 'Address'],
                        onSave: (values) async {
                          _branches = await ref.read(platformApiProvider).createTenantBranch(
                                widget.tenantId,
                                name: values[0],
                                address: values[1],
                              );
                          setState(() {});
                        },
                      ),
                      onDelete: (id) async {
                        _branches = await ref.read(platformApiProvider).deleteTenantBranch(widget.tenantId, id);
                        setState(() {});
                      },
                    ),
                  ),
                  _Section(
                    title: 'Fee plans',
                    subtitle: 'Membership packages for this gym only. Nothing is seeded — add plans here or let the gym create them.',
                    child: _NamedList(
                      rows: _feePlans,
                      empty: 'No fee plans yet. Add one if this gym should collect membership fees.',
                      subtitleOf: (r) {
                        final label = r['amountLabel'] as String? ??
                            '₹${(((r['amountMinor'] as num?)?.toInt() ?? 0) / 100).toStringAsFixed(2)}';
                        final cycle = r['billingCycle'] as String? ?? 'MONTHLY';
                        final def = r['isDefault'] == true ? ' · Default' : '';
                        return '$label · $cycle$def';
                      },
                      onEdit: (row) => _editFeePlan(row),
                      onAdd: () => _editFeePlan(null),
                      onDelete: (id) async {
                        _feePlans = await ref.read(platformApiProvider).deleteTenantFeePlan(widget.tenantId, id);
                        setState(() {});
                      },
                    ),
                  ),
                  _Section(
                    title: 'Extras catalogue',
                    subtitle: 'Protein, diet packs, and other items collected separately from membership fees.',
                    child: _NamedList(
                      rows: _addons,
                      empty: 'No extras yet. Seed Protein or Diet chart so the gym can collect at the desk.',
                      subtitleOf: (r) {
                        final minor = (r['amountMinor'] as num?)?.toInt() ?? 0;
                        return '₹${(minor / 100).toStringAsFixed(2)}';
                      },
                      onAdd: () => _promptAdd(
                        title: 'New extra',
                        fields: const ['Name', 'Amount in ₹'],
                        onSave: (values) async {
                          final rupees = double.tryParse(values[1]) ?? 0;
                          _addons = await ref.read(platformApiProvider).createTenantAddon(
                                widget.tenantId,
                                name: values[0],
                                amountMinor: (rupees * 100).round(),
                              );
                          setState(() {});
                        },
                      ),
                      onDelete: (id) async {
                        _addons = await ref.read(platformApiProvider).deleteTenantAddon(widget.tenantId, id);
                        setState(() {});
                      },
                    ),
                  ),
                  _Section(
                    title: 'Diet charts',
                    subtitle: 'Templates the gym sends to members on WhatsApp. Fee reminders are unchanged.',
                    child: _NamedList(
                      rows: _diets,
                      empty: 'No charts yet. Add a fat-loss or muscle-gain template.',
                      subtitleOf: (r) => (r['body'] as String?) ?? '',
                      onEdit: (row) async {
                        final name = TextEditingController(text: '${row['name'] ?? ''}');
                        final body = TextEditingController(text: '${row['body'] ?? ''}');
                        final ok = await showDialog<bool>(
                          context: context,
                          builder: (ctx) => AlertDialog(
                            title: const Text('Edit diet chart'),
                            content: SizedBox(
                              width: 520,
                              child: SingleChildScrollView(
                                child: Column(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    TextField(controller: name, decoration: const InputDecoration(labelText: 'Name')),
                                    const SizedBox(height: 12),
                                    DietEditor(controller: body),
                                  ],
                                ),
                              ),
                            ),
                            actions: [
                              TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
                              FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Save')),
                            ],
                          ),
                        );
                        if (ok == true && name.text.trim().isNotEmpty && body.text.trim().isNotEmpty) {
                          _diets = await ref.read(platformApiProvider).patchTenantDiet(
                                widget.tenantId,
                                row['id'] as String,
                                name: name.text.trim(),
                                body: body.text.trim(),
                              );
                          setState(() {});
                        }
                      },
                      onAdd: () async {
                        final name = TextEditingController();
                        final body = TextEditingController();
                        final ok = await showDialog<bool>(
                          context: context,
                          builder: (ctx) => AlertDialog(
                            title: const Text('Diet chart'),
                            content: SizedBox(
                              width: 520,
                              child: SingleChildScrollView(
                                child: Column(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    TextField(controller: name, decoration: const InputDecoration(labelText: 'Name')),
                                    const SizedBox(height: 12),
                                    DietEditor(controller: body),
                                  ],
                                ),
                              ),
                            ),
                            actions: [
                              TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
                              FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Add')),
                            ],
                          ),
                        );
                        if (ok == true && name.text.trim().isNotEmpty && body.text.trim().isNotEmpty) {
                          _diets = await ref.read(platformApiProvider).createTenantDiet(
                                widget.tenantId,
                                name: name.text.trim(),
                                body: body.text.trim(),
                              );
                          setState(() {});
                        }
                      },
                      onDelete: (id) async {
                        _diets = await ref.read(platformApiProvider).deleteTenantDiet(widget.tenantId, id);
                        setState(() {});
                      },
                    ),
                  ),
                  if (_error != null)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Text(_error!, style: TextStyle(color: scheme.error, fontWeight: FontWeight.w600)),
                    ),
                  FilledButton(
                    onPressed: _busy ? null : _save,
                    style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
                    child: Text(_busy ? 'Saving…' : 'Save tenant'),
                  ),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      OutlinedButton(
                        onPressed: _busy
                            ? null
                            : () async {
                                final tokens = await ref.read(platformApiProvider).impersonate(widget.tenantId);
                                if (mounted) {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    SnackBar(content: Text('Support session for ${tokens.user.fullName}')),
                                  );
                                  context.push('/tenants/${widget.tenantId}/members');
                                }
                              },
                        child: const Text('Open as owner'),
                      ),
                      OutlinedButton(
                        onPressed: _busy
                            ? null
                            : () async {
                                if (_status == 'SUSPENDED') {
                                  await ref.read(platformApiProvider).setStatus(widget.tenantId, suspend: false);
                                } else {
                                  if (!await _confirm('Suspend this business?', tenant.name)) {
                                    return;
                                  }
                                  await ref.read(platformApiProvider).setStatus(widget.tenantId, suspend: true);
                                }
                                ref.invalidate(tenantDetailProvider(widget.tenantId));
                                setState(() => _loaded = false);
                              },
                        child: Text(_status == 'SUSPENDED' ? 'Activate' : 'Suspend'),
                      ),
                      if (_status == 'CANCELLED')
                        OutlinedButton(
                          onPressed: _busy
                              ? null
                              : () async {
                                  await ref.read(platformApiProvider).restoreTenant(widget.tenantId);
                                  ref.invalidate(tenantDetailProvider(widget.tenantId));
                                  setState(() => _loaded = false);
                                },
                          child: const Text('Restore'),
                        ),
                      TextButton(
                        onPressed: _busy
                            ? null
                            : () async {
                                if (!await _confirm('Cancel this business?', tenant.name)) {
                                  return;
                                }
                                await ref.read(platformApiProvider).deleteTenant(widget.tenantId);
                                if (mounted) context.go('/tenants');
                              },
                        child: const Text('Cancel tenant'),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  _Section(
                    title: 'Internal notes',
                    child: Column(
                      children: [
                        TextField(controller: _note, decoration: const InputDecoration(labelText: 'Add a note')),
                        Align(
                          alignment: Alignment.centerLeft,
                          child: TextButton(
                            onPressed: () async {
                              if (_note.text.trim().isEmpty) {
                                return;
                              }
                              _notes = await ref.read(platformApiProvider).addNote(widget.tenantId, _note.text.trim());
                              _note.clear();
                              setState(() {});
                            },
                            child: const Text('Save note'),
                          ),
                        ),
                        ..._notes.map((n) => ListTile(
                              title: Text('${n['body']}'),
                              subtitle: Text('${n['author']} · ${formatLocalDateTime('${n['createdAt']}')}'),
                            )),
                      ],
                    ),
                  ),
                  _Section(
                    title: 'DueMate invoices',
                    child: Column(
                      children: [
                        TextField(controller: _invoicePeriod, decoration: const InputDecoration(labelText: 'Period (e.g. 2026-09)')),
                        TextField(controller: _invoiceAmount, decoration: const InputDecoration(labelText: 'Amount in paise')),
                        Align(
                          alignment: Alignment.centerLeft,
                          child: TextButton(
                            onPressed: () async {
                              final amount = int.tryParse(_invoiceAmount.text.trim()) ?? 0;
                              _invoices = await ref.read(platformApiProvider).addInvoice(
                                    widget.tenantId,
                                    periodLabel: _invoicePeriod.text.trim(),
                                    amountMinor: amount,
                                  );
                              setState(() {});
                            },
                            child: const Text('Add invoice'),
                          ),
                        ),
                        ..._invoices.map(
                          (i) => ListTile(
                            title: Text('${i['periodLabel']} · ${i['status']}'),
                            subtitle: Text('${i['amountMinor']} ${i['currency']}'),
                            trailing: i['status'] == 'DUE'
                                ? TextButton(
                                    onPressed: () async {
                                      _invoices = await ref.read(platformApiProvider).markInvoice(
                                            widget.tenantId,
                                            i['id'] as String,
                                            'PAID',
                                          );
                                      setState(() {});
                                    },
                                    child: const Text('Mark paid'),
                                  )
                                : null,
                          ),
                        ),
                      ],
                    ),
                  ),
                  _Section(
                    title: 'Audit',
                    child: Column(
                      children: _audit
                          .map((a) => ListTile(
                                dense: true,
                                title: Text('${a['action']}'),
                                subtitle: Text(formatLocalDateTime('${a['createdAt']}')),
                              ))
                          .toList(),
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Future<void> _editFeePlan(Map<String, dynamic>? row) async {
    final name = TextEditingController(text: '${row?['name'] ?? ''}');
    final amount = TextEditingController(
      text: row == null ? '' : (((row['amountMinor'] as num?)?.toInt() ?? 0) / 100).toStringAsFixed(2),
    );
    final grace = TextEditingController(text: '${row?['graceDays'] ?? 0}');
    var cycle = (row?['billingCycle'] as String?) ?? 'MONTHLY';
    var isDefault = row?['isDefault'] == true;
    const cycles = ['WEEKLY', 'MONTHLY', 'QUARTERLY', 'HALF_YEARLY', 'ANNUAL'];
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setLocal) => AlertDialog(
          title: Text(row == null ? 'New fee plan' : 'Edit fee plan'),
          content: SizedBox(
            width: 420,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(controller: name, decoration: const InputDecoration(labelText: 'Name')),
                const SizedBox(height: 12),
                TextField(
                  controller: amount,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(labelText: 'Amount in ₹'),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  value: cycles.contains(cycle) ? cycle : 'MONTHLY',
                  decoration: const InputDecoration(labelText: 'Billing cycle'),
                  items: [
                    for (final c in cycles) DropdownMenuItem(value: c, child: Text(c)),
                  ],
                  onChanged: (v) => setLocal(() => cycle = v ?? 'MONTHLY'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: grace,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Grace days'),
                ),
                CheckboxListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Default plan'),
                  value: isDefault,
                  onChanged: (v) => setLocal(() => isDefault = v ?? false),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: Text(row == null ? 'Add' : 'Save')),
          ],
        ),
      ),
    );
    if (ok != true || name.text.trim().isEmpty) {
      return;
    }
    final rupees = double.tryParse(amount.text.trim()) ?? 0;
    final days = int.tryParse(grace.text.trim()) ?? 0;
    final api = ref.read(platformApiProvider);
    if (row == null) {
      _feePlans = await api.createTenantFeePlan(
        widget.tenantId,
        name: name.text.trim(),
        amountMinor: (rupees * 100).round(),
        billingCycle: cycle,
        graceDays: days,
        isDefault: isDefault,
      );
    } else {
      _feePlans = await api.patchTenantFeePlan(
        widget.tenantId,
        '${row['id']}',
        name: name.text.trim(),
        amountMinor: (rupees * 100).round(),
        billingCycle: cycle,
        graceDays: days,
        isDefault: isDefault,
      );
    }
    if (mounted) {
      setState(() {});
    }
  }

  Future<void> _promptAdd({
    required String title,
    required List<String> fields,
    required Future<void> Function(List<String> values) onSave,
    bool multilineLast = false,
  }) async {
    final controllers = [for (final _ in fields) TextEditingController()];
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (var i = 0; i < fields.length; i++) ...[
              if (i > 0) const SizedBox(height: 8),
              TextField(
                controller: controllers[i],
                maxLines: multilineLast && i == fields.length - 1 ? 6 : 1,
                decoration: InputDecoration(labelText: fields[i]),
              ),
            ],
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Add')),
        ],
      ),
    );
    if (ok == true && controllers.first.text.trim().isNotEmpty) {
      await onSave(controllers.map((c) => c.text.trim()).toList());
    }
  }
}

class _HeroCard extends StatelessWidget {
  const _HeroCard({
    required this.name,
    required this.status,
    required this.members,
    required this.staff,
    required this.lastLogin,
    required this.lastCollection,
  });

  final String name;
  final String status;
  final String members;
  final String staff;
  final String lastLogin;
  final String lastCollection;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: LinearGradient(colors: [scheme.primary, scheme.primary.withValues(alpha: 0.82)]),
        borderRadius: BorderRadius.circular(24),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(name, style: Theme.of(context).textTheme.headlineSmall?.copyWith(color: scheme.onPrimary, fontWeight: FontWeight.w800)),
          const SizedBox(height: 8),
          Text('$status · $members members · $staff staff', style: TextStyle(color: scheme.onPrimary.withValues(alpha: 0.92))),
          const SizedBox(height: 4),
          Text('Last login $lastLogin · Last collection $lastCollection', style: TextStyle(color: scheme.onPrimary.withValues(alpha: 0.8), fontSize: 12)),
        ],
      ),
    );
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.child, this.subtitle});

  final String title;
  final String? subtitle;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
              if (subtitle != null) ...[
                const SizedBox(height: 4),
                Text(subtitle!, style: Theme.of(context).textTheme.bodySmall),
              ],
              const SizedBox(height: 16),
              child,
            ],
          ),
        ),
      ),
    );
  }
}

class _NamedList extends StatelessWidget {
  const _NamedList({
    required this.rows,
    required this.empty,
    required this.onAdd,
    required this.onDelete,
    this.onEdit,
    this.subtitleOf,
  });

  final List<Map<String, dynamic>> rows;
  final String empty;
  final VoidCallback onAdd;
  final Future<void> Function(String id) onDelete;
  final Future<void> Function(Map<String, dynamic> row)? onEdit;
  final String Function(Map<String, dynamic> row)? subtitleOf;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        if (rows.isEmpty) Align(alignment: Alignment.centerLeft, child: Text(empty)),
        for (final row in rows)
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: Text('${row['name']}'),
            subtitle: subtitleOf == null ? null : Text(subtitleOf!(row), maxLines: 2, overflow: TextOverflow.ellipsis),
            trailing: Wrap(
              children: [
                if (onEdit != null)
                  IconButton(icon: const Icon(Icons.edit_outlined), onPressed: () => onEdit!(row)),
                IconButton(icon: const Icon(Icons.delete_outline), onPressed: () => onDelete(row['id'] as String)),
              ],
            ),
          ),
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton.icon(onPressed: onAdd, icon: const Icon(Icons.add), label: const Text('Add')),
        ),
      ],
    );
  }
}

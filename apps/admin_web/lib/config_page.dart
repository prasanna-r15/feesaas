import 'package:feesaas_admin_web/admin_app_bar.dart';
import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final configFilterProvider = StateProvider<String>((ref) => '');

final platformConfigProvider = FutureProvider.autoDispose<List<PlatformConfigRow>>((ref) {
  final q = ref.watch(configFilterProvider);
  return ref.watch(platformApiProvider).listConfig(q: q);
});

class ConfigPage extends ConsumerStatefulWidget {
  const ConfigPage({super.key});

  @override
  ConsumerState<ConfigPage> createState() => _ConfigPageState();
}

class _ConfigPageState extends ConsumerState<ConfigPage> {
  final _search = TextEditingController();

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(platformConfigProvider);
    return AdminScaffold(
      title: 'Config',
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _edit(null),
        icon: const Icon(Icons.add),
        label: const Text('Add'),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: TextField(
              controller: _search,
              decoration: const InputDecoration(
                prefixIcon: Icon(Icons.search),
                labelText: 'Search key, customer type, or description',
              ),
              onChanged: (v) => ref.read(configFilterProvider.notifier).state = v,
            ),
          ),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 16),
            child: Text(
              'param_key is the setting name. param_sub_key is the customer type (DEFAULT, GYM, …). '
              'OTP placeholders: {{name}} {{otp}}. Dues placeholders: {{ownerName}} {{tenantName}} {{pendingCount}} {{total}} {{rows}}.',
            ),
          ),
          Expanded(
            child: async.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => Center(child: Text(problemOf(e).detail)),
              data: (rows) {
                if (rows.isEmpty) {
                  return const Center(child: Text('No config rows.'));
                }
                return ListView.separated(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 88),
                  itemCount: rows.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (context, i) {
                    final row = rows[i];
                    return ListTile(
                      title: Text('${row.paramKey}  ·  ${row.paramSubKey}'),
                      subtitle: Text(
                        [
                          if (row.description != null && row.description!.isNotEmpty) row.description!,
                          row.paramValue.length > 120 ? '${row.paramValue.substring(0, 120)}…' : row.paramValue,
                        ].join('\n'),
                        maxLines: 3,
                        overflow: TextOverflow.ellipsis,
                      ),
                      isThreeLine: true,
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (row.locked) const Chip(label: Text('System'), visualDensity: VisualDensity.compact),
                          IconButton(icon: const Icon(Icons.edit_outlined), onPressed: () => _edit(row)),
                          IconButton(
                            icon: const Icon(Icons.delete_outline),
                            onPressed: row.locked ? null : () => _delete(row),
                          ),
                        ],
                      ),
                      onTap: () => _edit(row),
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _delete(PlatformConfigRow row) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete config?'),
        content: Text('${row.paramKey} / ${row.paramSubKey} will be removed.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Delete')),
        ],
      ),
    );
    if (ok != true || !mounted) {
      return;
    }
    try {
      await ref.read(platformApiProvider).deleteConfig(row.id);
      ref.invalidate(platformConfigProvider);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(problemOf(e).detail)));
      }
    }
  }

  Future<void> _edit(PlatformConfigRow? existing) async {
    final created = await showDialog<bool>(
      context: context,
      builder: (ctx) => _ConfigEditor(existing: existing),
    );
    if (created == true) {
      ref.invalidate(platformConfigProvider);
    }
  }
}

class _ConfigEditor extends ConsumerStatefulWidget {
  const _ConfigEditor({this.existing});

  final PlatformConfigRow? existing;

  @override
  ConsumerState<_ConfigEditor> createState() => _ConfigEditorState();
}

class _ConfigEditorState extends ConsumerState<_ConfigEditor> {
  late final TextEditingController _key;
  late final TextEditingController _sub;
  late final TextEditingController _value;
  late final TextEditingController _description;
  var _busy = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    final e = widget.existing;
    _key = TextEditingController(text: e?.paramKey ?? '');
    _sub = TextEditingController(text: e?.paramSubKey ?? 'DEFAULT');
    _value = TextEditingController(text: e?.paramValue ?? '');
    _description = TextEditingController(text: e?.description ?? '');
  }

  @override
  void dispose() {
    _key.dispose();
    _sub.dispose();
    _value.dispose();
    _description.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final locked = widget.existing?.locked == true;
    return AlertDialog(
      title: Text(widget.existing == null ? 'New config' : 'Edit config'),
      content: SizedBox(
        width: 560,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: _key,
                enabled: !locked,
                decoration: const InputDecoration(labelText: 'param_key', hintText: 'SIGNUP_OTP_BODY'),
              ),
              TextField(
                controller: _sub,
                enabled: !locked,
                decoration: const InputDecoration(labelText: 'param_sub_key (customer type)', hintText: 'DEFAULT or GYM'),
              ),
              TextField(
                controller: _description,
                decoration: const InputDecoration(labelText: 'Description'),
              ),
              TextField(
                controller: _value,
                minLines: 6,
                maxLines: 16,
                decoration: const InputDecoration(labelText: 'param_value'),
              ),
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(onPressed: _busy ? null : () => Navigator.pop(context, false), child: const Text('Cancel')),
        FilledButton(onPressed: _busy ? null : _save, child: Text(_busy ? 'Saving…' : 'Save')),
      ],
    );
  }

  Future<void> _save() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final existing = widget.existing;
      if (existing == null) {
        await ref.read(platformApiProvider).createConfig(
              paramKey: _key.text.trim(),
              paramSubKey: _sub.text.trim(),
              paramValue: _value.text,
              description: _description.text.trim(),
            );
      } else {
        await ref.read(platformApiProvider).updateConfig(
              existing.id,
              paramKey: _key.text.trim(),
              paramSubKey: _sub.text.trim(),
              paramValue: _value.text,
              description: _description.text.trim(),
            );
      }
      if (mounted) {
        Navigator.pop(context, true);
      }
    } catch (e) {
      setState(() {
        _error = problemOf(e).detail;
        _busy = false;
      });
    }
  }
}

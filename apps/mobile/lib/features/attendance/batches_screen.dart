import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

final batchesProvider = FutureProvider.autoDispose<List<Batch>>((ref) {
  return ref.watch(attendanceApiProvider).listBatches();
});

class BatchesScreen extends ConsumerWidget {
  const BatchesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(batchesProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Batches')),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _create(context, ref),
        child: const Icon(Icons.add),
      ),
      body: async.when(
        loading: () => const FsLoading(),
        error: (e, _) => FsErrorState(
          message: problemOf(e).detail,
          onRetry: () => ref.invalidate(batchesProvider),
        ),
        data: (rows) {
          if (rows.isEmpty) {
            return const FsEmptyState(
              title: 'No batches yet',
              message: 'Create a class or batch, add members, then mark attendance.',
            );
          }
          return ListView.separated(
            itemCount: rows.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (context, i) {
              final b = rows[i];
              return ListTile(
                title: Text(b.name),
                subtitle: Text([
                  '${b.memberCount} members',
                  if (b.schedule != null && b.schedule!.isNotEmpty) b.schedule!,
                ].join(' · ')),
                onTap: () => context.push('/more/batches/${b.id}'),
                trailing: Wrap(
                  spacing: 0,
                  children: [
                    IconButton(
                      tooltip: 'Add members',
                      icon: const Icon(Icons.person_add_outlined),
                      onPressed: () => _addMembers(context, ref, b.id),
                    ),
                    IconButton(
                      icon: const Icon(Icons.delete_outline),
                      onPressed: () async {
                        await ref.read(attendanceApiProvider).deleteBatch(b.id);
                        ref.invalidate(batchesProvider);
                      },
                    ),
                  ],
                ),
              );
            },
          );
        },
      ),
    );
  }

  Future<void> _create(BuildContext context, WidgetRef ref) async {
    final name = TextEditingController();
    final schedule = TextEditingController();
    final ok = await showFsSheet<bool>(
      context: context,
      builder: (ctx) => FsSheetForm(
        title: 'New batch',
        body: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(controller: name, decoration: const InputDecoration(labelText: 'Name')),
            TextField(controller: schedule, decoration: const InputDecoration(labelText: 'Schedule (optional)')),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Save')),
        ],
      ),
    );
    if (ok != true || name.text.trim().isEmpty) {
      return;
    }
    try {
      await ref.read(attendanceApiProvider).createBatch(name: name.text.trim(), schedule: schedule.text.trim());
      ref.invalidate(batchesProvider);
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(problemOf(e).detail)));
      }
    }
  }
}

Future<void> _addMembers(BuildContext context, WidgetRef ref, String batchId) async {
  try {
    final customers = await ref.read(customerApiProvider).list();
    final existing = await ref.read(attendanceApiProvider).members(batchId);
    if (!context.mounted) {
      return;
    }
    if (customers.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Add gym members first, then attach them to this batch.')),
      );
      return;
    }
    final already = existing.map((m) => m.id).toSet();
    final selected = <String>{};
    final ok = await showFsSheet<bool>(
      context: context,
      builder: (ctx) {
        var query = '';
        return StatefulBuilder(
          builder: (ctx, setLocal) {
            final q = query.trim().toLowerCase();
            final visible = customers.where((c) {
              if (already.contains(c.id)) {
                return false;
              }
              if (q.isEmpty) {
                return true;
              }
              return c.fullName.toLowerCase().contains(q) || (c.phone ?? '').contains(q);
            }).toList();
            return SizedBox(
              height: 420,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text('Add members to batch', style: Theme.of(ctx).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
                  const SizedBox(height: 12),
                  TextField(
                    decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'Search member'),
                    onChanged: (v) => setLocal(() => query = v),
                  ),
                  const SizedBox(height: 8),
                  Expanded(
                    child: visible.isEmpty
                        ? const Center(child: Text('No members left to add.'))
                        : ListView(
                            children: [
                              for (final c in visible)
                                CheckboxListTile(
                                  dense: true,
                                  value: selected.contains(c.id),
                                  title: Text(c.fullName),
                                  subtitle: Text([c.customerCode, c.phone].whereType<String>().where((s) => s.isNotEmpty).join(' · ')),
                                  onChanged: (on) => setLocal(() {
                                    if (on == true) {
                                      selected.add(c.id);
                                    } else {
                                      selected.remove(c.id);
                                    }
                                  }),
                                ),
                            ],
                          ),
                  ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
                      FilledButton(onPressed: () => Navigator.pop(ctx, true), child: Text('Add (${selected.length})')),
                    ],
                  ),
                ],
              ),
            );
          },
        );
      },
    );
    if (ok != true || selected.isEmpty) {
      return;
    }
    for (final id in selected) {
      await ref.read(attendanceApiProvider).addMember(batchId, id);
    }
    ref.invalidate(batchesProvider);
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Added ${selected.length} member(s).')));
    }
  } catch (e) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(problemOf(e).detail)));
    }
  }
}

class AttendanceScreen extends ConsumerStatefulWidget {
  const AttendanceScreen({super.key, required this.batchId});

  final String batchId;

  @override
  ConsumerState<AttendanceScreen> createState() => _AttendanceScreenState();
}

class _AttendanceScreenState extends ConsumerState<AttendanceScreen> {
  late String _on;
  AttendanceRoster? _roster;
  Object? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _on = '${now.year.toString().padLeft(4, '0')}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final roster = await ref.read(attendanceApiProvider).roster(batchId: widget.batchId, on: _on);
      if (mounted) {
        setState(() {
          _roster = roster;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e;
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Attendance'),
        actions: [
          IconButton(
            tooltip: 'Add member',
            onPressed: () => _addMembers(context, ref, widget.batchId).then((_) {
              if (mounted) {
                _load();
              }
            }),
            icon: const Icon(Icons.person_add_outlined),
          ),
        ],
      ),
      body: Column(
        children: [
          ListTile(
            title: Text('Date $_on'),
            trailing: TextButton(
              onPressed: () async {
                final picked = await showDatePicker(
                  context: context,
                  initialDate: DateTime.tryParse(_on) ?? DateTime.now(),
                  firstDate: DateTime(2020),
                  lastDate: DateTime(2100),
                );
                if (picked != null) {
                  setState(() {
                    _on =
                        '${picked.year.toString().padLeft(4, '0')}-${picked.month.toString().padLeft(2, '0')}-${picked.day.toString().padLeft(2, '0')}';
                  });
                  await _load();
                }
              },
              child: const Text('Change'),
            ),
          ),
          Expanded(child: _body()),
        ],
      ),
    );
  }

  Widget _body() {
    if (_loading) {
      return const FsLoading();
    }
    if (_error != null) {
      return FsErrorState(message: problemOf(_error!).detail, onRetry: _load);
    }
    final members = _roster?.members ?? const <AttendanceMember>[];
    if (members.isEmpty) {
      return const FsEmptyState(title: 'No members in this batch', message: 'Add members to mark attendance.');
    }
    return ListView.separated(
      itemCount: members.length,
      separatorBuilder: (_, __) => const Divider(height: 1),
      itemBuilder: (context, i) {
        final m = members[i];
        return ListTile(
          title: Text(m.fullName),
          subtitle: Text(m.customerCode),
          trailing: Wrap(
            spacing: 4,
            children: [
              _MarkChip(
                label: 'P',
                selected: m.status == 'PRESENT',
                onTap: () => _mark(m.id, 'PRESENT'),
              ),
              _MarkChip(
                label: 'A',
                selected: m.status == 'ABSENT',
                onTap: () => _mark(m.id, 'ABSENT'),
              ),
            ],
          ),
        );
      },
    );
  }

  Future<void> _mark(String customerId, String status) async {
    try {
      final roster = await ref.read(attendanceApiProvider).mark(
            batchId: widget.batchId,
            customerId: customerId,
            status: status,
            markedOn: _on,
          );
      setState(() => _roster = roster);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(problemOf(e).detail)));
      }
    }
  }
}

class _MarkChip extends StatelessWidget {
  const _MarkChip({required this.label, required this.selected, required this.onTap});

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return FilterChip(
      label: Text(label),
      selected: selected,
      onSelected: (_) => onTap(),
    );
  }
}

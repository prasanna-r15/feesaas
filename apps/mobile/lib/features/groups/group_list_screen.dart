import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

final groupsListProvider = FutureProvider<List<ExpenseGroup>>((ref) {
  return ref.watch(groupsApiProvider).list();
});

class GroupListScreen extends ConsumerWidget {
  const GroupListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(groupsListProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Groups'),
        actions: [
          IconButton(
            tooltip: 'Join with code',
            onPressed: () => _join(context),
            icon: const Icon(Icons.vpn_key_outlined),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _create(context, ref),
        icon: const Icon(Icons.add),
        label: const Text('New group'),
      ),
      body: async.when(
        loading: () => const FsLoading(),
        error: (e, _) => FsErrorState(message: problemOf(e).detail, onRetry: () => ref.invalidate(groupsListProvider)),
        data: (items) {
          if (items.isEmpty) {
            return FsEmptyState(
              title: 'Split bills with friends',
              message: 'Private until you invite them.',
              action: FilledButton(onPressed: () => _create(context, ref), child: const Text('Create a group')),
            );
          }
          return ListView.separated(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 96),
            itemCount: items.length,
            separatorBuilder: (_, __) => const SizedBox(height: 10),
            itemBuilder: (context, i) {
              final g = items[i];
              return FsCard(
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  leading: CircleAvatar(
                    child: Text(g.name.trim().isEmpty ? 'G' : g.name.trim().substring(0, 1).toUpperCase()),
                  ),
                  title: Text(g.name, style: const TextStyle(fontWeight: FontWeight.w800)),
                  subtitle: Text(_typeLabel(g.type)),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => context.push('/groups/${g.id}'),
                ),
              );
            },
          );
        },
      ),
    );
  }

  String _typeLabel(String type) {
    return switch (type) {
      'TRIP' => 'Trip',
      'DINNER' => 'Dinner',
      'ROOMMATES' => 'Roommates',
      'FRIENDS' => 'Friends',
      'EVENT' => 'Event',
      'FAMILY' => 'Family',
      _ => 'Group',
    };
  }

  Future<void> _join(BuildContext context) async {
    final code = TextEditingController();
    await showFsSheet<void>(
      context: context,
      builder: (ctx) => FsSheetForm(
        title: 'Join with invite code',
        body: TextField(
          controller: code,
          decoration: const InputDecoration(labelText: 'Invite code'),
          autofocus: true,
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(
            onPressed: () {
              final token = code.text.trim();
              if (token.isEmpty) return;
              Navigator.pop(ctx);
              context.go('/group/invite/$token');
            },
            child: const Text('Continue'),
          ),
        ],
      ),
    );
  }

  Future<void> _create(BuildContext context, WidgetRef ref) async {
    final name = TextEditingController();
    String type = 'TRIP';
    await showFsSheet<void>(
      context: context,
      builder: (ctx) => FsSheetForm(
        title: 'New group',
        body: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(controller: name, decoration: const InputDecoration(labelText: 'Name')),
            DropdownButtonFormField<String>(
              value: type,
              items: const [
                DropdownMenuItem(value: 'TRIP', child: Text('Trip')),
                DropdownMenuItem(value: 'DINNER', child: Text('Dinner')),
                DropdownMenuItem(value: 'ROOMMATES', child: Text('Roommates')),
                DropdownMenuItem(value: 'FRIENDS', child: Text('Friends')),
                DropdownMenuItem(value: 'EVENT', child: Text('Event')),
                DropdownMenuItem(value: 'FAMILY', child: Text('Family')),
                DropdownMenuItem(value: 'CUSTOM', child: Text('Custom')),
              ],
              onChanged: (v) => type = v ?? 'CUSTOM',
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(
            onPressed: () async {
              if (name.text.trim().isEmpty) return;
              final g = await ref.read(groupsApiProvider).create(name: name.text.trim(), type: type);
              ref.invalidate(groupsListProvider);
              if (ctx.mounted) Navigator.pop(ctx);
              if (context.mounted) context.push('/groups/${g.id}');
            },
            child: const Text('Create'),
          ),
        ],
      ),
    );
  }
}

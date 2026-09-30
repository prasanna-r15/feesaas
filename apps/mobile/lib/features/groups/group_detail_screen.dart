import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/util/money.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

final groupBoardProvider = FutureProvider.autoDispose.family<_Board, String>((ref, groupId) async {
  final api = ref.watch(groupsApiProvider);
  final listed = await api.list();
  final balances = await api.balances(groupId);
  final expenses = await api.expenses(groupId);
  final members = await api.members(groupId);
  ExpenseGroup? fromList;
  for (final g in listed) {
    if (g.id == groupId) {
      fromList = g;
      break;
    }
  }
  final group = fromList ?? ExpenseGroup(id: groupId, name: balances.groupName, type: 'CUSTOM');
  return _Board(group: group, balances: balances, expenses: expenses, members: members);
});

class _Board {
  const _Board({
    required this.group,
    required this.balances,
    required this.expenses,
    required this.members,
  });

  final ExpenseGroup group;
  final GroupBalances balances;
  final List<GroupExpenseItem> expenses;
  final List<GroupMember> members;
}

class GroupDetailScreen extends ConsumerWidget {
  const GroupDetailScreen({super.key, required this.groupId});

  final String groupId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(groupBoardProvider(groupId));
    return async.when(
      loading: () => const Scaffold(body: FsLoading()),
      error: (e, _) => Scaffold(
        appBar: AppBar(leading: BackButton(onPressed: () => _back(context))),
        body: FsErrorState(message: problemOf(e).detail, onRetry: () => ref.invalidate(groupBoardProvider(groupId))),
      ),
      data: (board) {
        final scheme = Theme.of(context).colorScheme;
        final net = board.balances.myNetMinor;
        final youGet = net > 0;
        final even = net == 0;
        final people = board.members.where((m) => m.status == 'ACTIVE').toList();
        return Scaffold(
          appBar: AppBar(
            leading: BackButton(onPressed: () => _back(context)),
            title: Text(board.group.name),
            actions: [
              _HintEye(
                title: 'How this group works',
                body:
                    'Amounts between two people cancel out. If they owe you ₹600 and you owe them ₹500, they pay you ₹100.\n\nEveryone can see a split. Only the person who created it can edit it or tap Received when someone has paid.',
              ),
              IconButton(
                tooltip: 'Invite on WhatsApp',
                onPressed: () => _invite(context, ref, board),
                icon: const Icon(Icons.ios_share_outlined),
              ),
            ],
          ),
          floatingActionButton: FloatingActionButton.extended(
            onPressed: () => _editSplit(context, ref, members: board.members),
            icon: const Icon(Icons.add),
            label: const Text('Add split'),
          ),
          body: RefreshIndicator(
            onRefresh: () async => ref.invalidate(groupBoardProvider(groupId)),
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 112),
              children: [
                FsCard(
                  child: Container(
                    width: double.infinity,
                    padding: const EdgeInsets.fromLTRB(18, 20, 18, 18),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(20),
                      gradient: LinearGradient(
                        colors: even
                            ? [scheme.surfaceContainerHighest, scheme.surface]
                            : [scheme.primary, scheme.tertiary],
                      ),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          even ? 'Settled' : youGet ? 'You get' : 'You pay',
                          style: TextStyle(
                            color: even ? scheme.onSurfaceVariant : scheme.onPrimary.withValues(alpha: 0.9),
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          even ? '₹0' : rupees(youGet ? net : -net),
                          style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                                fontWeight: FontWeight.w800,
                                color: even ? scheme.onSurface : scheme.onPrimary,
                              ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          'Group spent ${rupees(board.balances.totalMinor)}',
                          style: TextStyle(
                            color: even ? scheme.onSurfaceVariant : scheme.onPrimary.withValues(alpha: 0.85),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: Text('To settle', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
                    ),
                    _HintEye(
                      title: 'To settle',
                      body: 'This is the leftover after tallying. It is pending until the split creator taps Received on that person’s share.',
                    ),
                  ],
                ),
                if (board.balances.suggested.isEmpty)
                  const FsCard(
                    child: ListTile(
                      leading: Icon(Icons.check_circle_outline),
                      title: Text('No one owes anyone'),
                    ),
                  )
                else
                  for (final line in board.balances.suggested)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: FsCard(
                        child: ListTile(
                          leading: CircleAvatar(
                            backgroundColor: line.youReceive ? scheme.primaryContainer : scheme.tertiaryContainer,
                            child: Text(
                              (line.youReceive ? line.fromName : line.toName).trim().isEmpty
                                  ? '?'
                                  : (line.youReceive ? line.fromName : line.toName).trim().substring(0, 1).toUpperCase(),
                            ),
                          ),
                          title: Text(
                            line.youReceive
                                ? '${line.fromName} → You'
                                : line.youPay
                                    ? 'You → ${line.toName}'
                                    : '${line.fromName} → ${line.toName}',
                            style: const TextStyle(fontWeight: FontWeight.w700),
                          ),
                          trailing: Text(rupees(line.amountMinor), style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
                        ),
                      ),
                    ),
                const SizedBox(height: 8),
                Text('Paid in this group', style: Theme.of(context).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
                const SizedBox(height: 6),
                SizedBox(
                  height: 72,
                  child: ListView(
                    scrollDirection: Axis.horizontal,
                    children: [
                      for (final row in board.balances.paid)
                        Padding(
                          padding: const EdgeInsets.only(right: 8),
                          child: FsCard(
                            child: Padding(
                              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(row.name, style: const TextStyle(fontWeight: FontWeight.w700)),
                                  Text(rupees(row.paidMinor)),
                                ],
                              ),
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: Text('People', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
                    ),
                    TextButton.icon(
                      onPressed: () => _addPerson(context, ref),
                      icon: const Icon(Icons.person_add_alt_1, size: 18),
                      label: const Text('Add'),
                    ),
                  ],
                ),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final m in people)
                      Chip(
                        avatar: CircleAvatar(
                          child: Text(m.displayName.trim().isEmpty ? '?' : m.displayName.trim().substring(0, 1).toUpperCase()),
                        ),
                        label: Text(m.displayName),
                      ),
                  ],
                ),
                const SizedBox(height: 20),
                Text('Splits', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
                const SizedBox(height: 8),
                if (board.expenses.isEmpty)
                  const FsCard(
                    child: ListTile(title: Text('No splits yet'), subtitle: Text('Tap Add split')),
                  )
                else
                  for (final expense in board.expenses)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: FsCard(
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(12, 8, 4, 8),
                          child: Column(
                            children: [
                              ListTile(
                                contentPadding: const EdgeInsets.only(left: 4),
                                title: Text(rupees(expense.amountMinor), style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 18)),
                                subtitle: Text('${expense.payerName} paid'),
                                trailing: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    _HintEye(
                                      title: expense.description,
                                      body: [
                                        'Paid by ${expense.payerName}',
                                        'Total ${rupees(expense.amountMinor)}',
                                        '',
                                        for (final s in expense.shares)
                                          '${s.displayName}: ${rupees(s.shareMinor)}${s.isPayer ? ' · covered' : s.settled ? ' · paid' : ' · to pay'}',
                                      ].join('\n'),
                                    ),
                                    if (expense.canEdit)
                                      IconButton(
                                        tooltip: 'Edit split',
                                        onPressed: () => _editSplit(context, ref, members: board.members, existing: expense),
                                        icon: const Icon(Icons.edit_outlined, size: 20),
                                      ),
                                  ],
                                ),
                              ),
                              for (final share in expense.shares)
                                ListTile(
                                  dense: true,
                                  leading: Icon(
                                    share.isPayer
                                        ? Icons.paid_outlined
                                        : share.settled
                                            ? Icons.check_circle
                                            : Icons.schedule,
                                    size: 20,
                                    color: share.settled || share.isPayer ? scheme.primary : scheme.outline,
                                  ),
                                  title: Text(share.displayName),
                                  trailing: Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      Text(rupees(share.shareMinor), style: const TextStyle(fontWeight: FontWeight.w700)),
                                      if (expense.canEdit && !share.isPayer && !share.settled)
                                        TextButton(
                                          onPressed: () async {
                                            await ref.read(groupsApiProvider).markSharePaid(groupId, expense.id, share.memberId);
                                            ref.invalidate(groupBoardProvider(groupId));
                                          },
                                          child: const Text('Received'),
                                        ),
                                    ],
                                  ),
                                ),
                            ],
                          ),
                        ),
                      ),
                    ),
              ],
            ),
          ),
        );
      },
    );
  }

  void _back(BuildContext context) {
    if (context.canPop()) {
      context.pop();
    } else {
      context.go('/groups');
    }
  }

  Future<void> _invite(BuildContext context, WidgetRef ref, _Board board) async {
    final ticket = await ref.read(groupsApiProvider).invite(groupId);
    final origin = Uri.base.hasScheme && (Uri.base.scheme == 'http' || Uri.base.scheme == 'https')
        ? Uri.base.origin
        : '';
    final link = origin.isEmpty ? ticket.path : '$origin${ticket.path}';
    final message =
        'Join *${ticket.groupName}* on DueMate (${ticket.memberCount} ${ticket.memberCount == 1 ? 'person' : 'people'}).\n\nOpen this invite:\n$link';
    await Clipboard.setData(ClipboardData(text: '$link\nCode: ${ticket.token}'));
    final wa = Uri.parse('https://wa.me/?text=${Uri.encodeComponent(message)}');
    if (!context.mounted) return;
    await showModalBottomSheet<void>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Invite to ${board.group.name}', style: Theme.of(ctx).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
              const SizedBox(height: 8),
              Text('Anyone with this link can join. Link copied.'),
              const SizedBox(height: 16),
              FilledButton.icon(
                onPressed: () => launchUrl(wa, mode: LaunchMode.externalApplication),
                icon: const Icon(Icons.chat_outlined),
                label: const Text('Share on WhatsApp'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _addPerson(BuildContext context, WidgetRef ref) async {
    final name = TextEditingController();
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Add a name'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(controller: name, decoration: const InputDecoration(labelText: 'Name on this split')),
            const SizedBox(height: 8),
            const Text('This only adds a placeholder. They see the group after they open your invite link.'),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(
            onPressed: () async {
              if (name.text.trim().isEmpty) return;
              await ref.read(groupsApiProvider).addMember(groupId, displayName: name.text.trim());
              ref.invalidate(groupBoardProvider(groupId));
              if (ctx.mounted) Navigator.pop(ctx);
            },
            child: const Text('Add'),
          ),
        ],
      ),
    );
  }

  Future<void> _editSplit(
    BuildContext context,
    WidgetRef ref, {
    required List<GroupMember> members,
    GroupExpenseItem? existing,
  }) async {
    final active = members.where((m) => m.status == 'ACTIVE').toList();
    final desc = TextEditingController(text: existing?.description ?? '');
    final amount = TextEditingController(
      text: existing == null ? '' : (existing.amountMinor / 100).toStringAsFixed(existing.amountMinor % 100 == 0 ? 0 : 2),
    );
    String? paidBy;
    for (final m in active) {
      if (m.userId != null && m.userId == ref.read(tenantConfigProvider)?.user.id) {
        paidBy = m.id;
        break;
      }
    }
    if (existing == null && paidBy == null) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Join this group with the invite before adding a split.')),
        );
      }
      return;
    }
    if (existing != null) {
      paidBy = existing.paidBy;
    }
    final myName = active.where((m) => m.id == paidBy).map((m) => m.displayName).firstWhere((_) => true, orElse: () => 'You');
    final selected = {
      for (final m in active) m.id: existing == null ? true : existing.shares.any((s) => s.memberId == m.id),
    };
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => Padding(
        padding: EdgeInsets.only(left: 20, right: 20, top: 16, bottom: MediaQuery.viewInsetsOf(ctx).bottom + 24),
        child: StatefulBuilder(
          builder: (ctx, setLocal) => SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(existing == null ? 'New split' : 'Edit split', style: Theme.of(ctx).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
                const SizedBox(height: 8),
                TextField(controller: desc, decoration: const InputDecoration(labelText: 'What for?')),
                TextField(controller: amount, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Amount (₹)')),
                Padding(
                  padding: const EdgeInsets.only(top: 12, bottom: 4),
                  child: Text(
                    existing == null ? 'Paid by you ($myName)' : 'Paid by $myName',
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                ),
                if (existing == null)
                  const Text('You can only record a split you paid. Others add their own spends.'),
                const SizedBox(height: 8),
                const Text('Split equally between'),
                for (final m in active)
                  CheckboxListTile(
                    value: selected[m.id] ?? false,
                    title: Text(m.displayName),
                    onChanged: (v) => setLocal(() => selected[m.id] = v ?? false),
                  ),
                FilledButton(
                  onPressed: () async {
                    final rupee = double.tryParse(amount.text.trim());
                    if (rupee == null || paidBy == null || desc.text.trim().isEmpty) return;
                    final shareMembers = active.where((m) => selected[m.id] == true).toList();
                    if (shareMembers.isEmpty) return;
                    final payload = (
                      description: desc.text.trim(),
                      amountMinor: (rupee * 100).round(),
                      paidBy: paidBy!,
                      splitMethod: 'EQUAL',
                      shares: [for (final m in shareMembers) {'memberId': m.id}],
                    );
                    if (existing == null) {
                      await ref.read(groupsApiProvider).addExpense(
                            groupId,
                            description: payload.description,
                            amountMinor: payload.amountMinor,
                            paidBy: payload.paidBy,
                            splitMethod: payload.splitMethod,
                            shares: payload.shares,
                          );
                    } else {
                      await ref.read(groupsApiProvider).updateExpense(
                            groupId,
                            existing.id,
                            description: payload.description,
                            amountMinor: payload.amountMinor,
                            paidBy: payload.paidBy,
                            splitMethod: payload.splitMethod,
                            shares: payload.shares,
                          );
                    }
                    ref.invalidate(groupBoardProvider(groupId));
                    if (ctx.mounted) Navigator.pop(ctx);
                  },
                  child: Text(existing == null ? 'Save split' : 'Update split'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _HintEye extends StatelessWidget {
  const _HintEye({required this.title, required this.body});

  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    return IconButton(
      tooltip: title,
      icon: const Icon(Icons.visibility_outlined),
      onPressed: () {
        showDialog<void>(
          context: context,
          builder: (ctx) => AlertDialog(
            title: Text(title),
            content: SingleChildScrollView(child: Text(body)),
            actions: [TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Close'))],
          ),
        );
      },
    );
  }
}

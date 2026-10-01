import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/features/auth/workspace_switcher.dart';
import 'package:feesaas_mobile/features/catalog/catalog_screens.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

final pendingBucketProvider = StateProvider.autoDispose<String>((ref) => '');
final pendingBranchFilterProvider = StateProvider.autoDispose<String?>((ref) => null);

final pendingFeesProvider = FutureProvider.autoDispose<List<PendingFee>>((ref) {
  ref.watch(workspaceClockProvider);
  final bucket = ref.watch(pendingBucketProvider);
  final branchId = ref.watch(pendingBranchFilterProvider);
  return ref.watch(feeApiProvider).pending(bucket: bucket.isEmpty ? null : bucket, branchId: branchId);
});

final pendingSummaryProvider = FutureProvider.autoDispose<PendingSummary>((ref) {
  ref.watch(workspaceClockProvider);
  return ref.watch(feeApiProvider).summary();
});

class PendingFeesScreen extends ConsumerWidget {
  const PendingFeesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final fees = ref.watch(pendingFeesProvider);
    final summary = ref.watch(pendingSummaryProvider);
    final bucket = ref.watch(pendingBucketProvider);
    return Scaffold(
      appBar: AppBar(
        leading: const Padding(padding: EdgeInsets.all(6), child: DueMateLogo(height: 36)),
        title: const Text('Pending fees'),
        actions: const [WorkspaceSwitcher()],
      ),
      body: Column(
        children: [
          summary.when(
            loading: () => const SizedBox(height: 8),
            error: (_, __) => const SizedBox.shrink(),
            data: (s) => Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
              child: Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  _BucketChip(
                    label: 'All · ${s.allLabel}',
                    selected: bucket.isEmpty,
                    onTap: () => ref.read(pendingBucketProvider.notifier).state = '',
                  ),
                  _BucketChip(
                    label: 'Overdue · ${s.overdueCount}',
                    selected: bucket == 'overdue',
                    onTap: () => ref.read(pendingBucketProvider.notifier).state = 'overdue',
                  ),
                  _BucketChip(
                    label: 'Today · ${s.todayCount}',
                    selected: bucket == 'today',
                    onTap: () => ref.read(pendingBucketProvider.notifier).state = 'today',
                  ),
                ],
              ),
            ),
          ),
          BranchFilterBar(
            value: ref.watch(pendingBranchFilterProvider),
            onChanged: (v) => ref.read(pendingBranchFilterProvider.notifier).state = v,
          ),
          Expanded(
            child: fees.when(
              loading: () => const FsLoading(),
              error: (e, _) => FsErrorState(
                message: problemOf(e).detail,
                onRetry: () {
                  ref.invalidate(pendingFeesProvider);
                  ref.invalidate(pendingSummaryProvider);
                },
              ),
              data: (rows) {
                if (rows.isEmpty) {
                  return const FsEmptyState(
                    title: 'No pending fees yet',
                    message: 'Add a member with a due date. A monthly fee is created automatically.',
                  );
                }
                return ListView.separated(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
                  itemCount: rows.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemBuilder: (context, i) {
                    final fee = rows[i];
                    return FsEnter(
                      delay: Duration(milliseconds: 40 * (i > 8 ? 8 : i)),
                      child: FsCard(
                        onTap: () => _collect(context, ref, fee),
                        child: ListTile(
                          contentPadding: const EdgeInsets.fromLTRB(16, 10, 8, 10),
                          title: Text(fee.customerName, style: const TextStyle(fontWeight: FontWeight.w700)),
                          subtitle: Text(
                            [
                              fee.customerCode,
                              if (fee.branchName != null) fee.branchName!,
                              if (fee.planName != null) fee.planName!,
                              'Due ${fee.dueDate}',
                            ].where((s) => s.isNotEmpty).join(' · '),
                          ),
                          trailing: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                tooltip: fee.hasWhatsapp ? 'Remind on WhatsApp' : 'Remind by SMS',
                                onPressed: () => _remind(context, ref, fee),
                                icon: Icon(fee.hasWhatsapp ? Icons.chat_outlined : Icons.sms_outlined),
                              ),
                              Column(
                                mainAxisAlignment: MainAxisAlignment.center,
                                crossAxisAlignment: CrossAxisAlignment.end,
                                children: [
                                  Text(fee.outstandingLabel, style: const TextStyle(fontWeight: FontWeight.w800)),
                                  const SizedBox(height: 4),
                                  FsStatusChip(
                                    label: fee.isOverdue ? 'Overdue' : fee.effectiveStatus,
                                    tone: fee.isOverdue ? FsTone.danger : FsTone.warning,
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ),
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

  Future<void> _collect(BuildContext context, WidgetRef ref, PendingFee fee) async {
    String method = 'CASH';
    final reference = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setLocal) => AlertDialog(
          title: Text('Collect ${fee.outstandingLabel}'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(fee.customerName),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                value: method,
                decoration: const InputDecoration(labelText: 'Method'),
                items: const [
                  DropdownMenuItem(value: 'CASH', child: Text('Cash')),
                  DropdownMenuItem(value: 'UPI', child: Text('UPI')),
                  DropdownMenuItem(value: 'CARD', child: Text('Card')),
                  DropdownMenuItem(value: 'BANK', child: Text('Bank')),
                  DropdownMenuItem(value: 'OTHER', child: Text('Other')),
                ],
                onChanged: (v) => setLocal(() => method = v ?? 'CASH'),
              ),
              TextField(
                controller: reference,
                decoration: const InputDecoration(labelText: 'Reference (UPI/UTR, optional)'),
              ),
            ],
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Collect')),
          ],
        ),
      ),
    );
    if (confirmed != true) {
      return;
    }
    try {
      final paid = await ref.read(feeApiProvider).collect(
            fee.id,
            method: method,
            referenceNo: reference.text.trim().isEmpty ? null : reference.text.trim(),
          );
      ref.invalidate(pendingFeesProvider);
      ref.invalidate(pendingSummaryProvider);
      tickWorkspace(ref);
      if (context.mounted) {
        await showDialog<void>(
          context: context,
          builder: (ctx) => AlertDialog(
            title: const Text('Receipt'),
            content: Text(
              '${paid.receiptNo ?? 'Recorded'}\n${fee.customerName}\n${fee.outstandingLabel} · $method',
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('OK')),
            ],
          ),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(problemOf(e).detail)));
      }
    }
  }

  Future<void> _remind(BuildContext context, WidgetRef ref, PendingFee fee) async {
    try {
      final payload = await ref.read(feeApiProvider).remind(fee.id);
      final primary = payload.preferWhatsapp ? payload.waLink : payload.smsLink;
      final fallback = payload.preferWhatsapp ? payload.smsLink : payload.waLink;
      var opened = await _openOutbound(primary);
      if (!opened) {
        opened = await _openOutbound(fallback);
      }
      if (!opened && context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Could not open WhatsApp or Messages. Check the member has a phone.')),
        );
      } else if (context.mounted && !payload.preferWhatsapp) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Opening Messages. The text is sent from this phone’s SIM.')),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(problemOf(e).detail)));
      }
    }
  }

  Future<bool> _openOutbound(String url) async {
    Future<bool> tryUrl(String value) async {
      try {
        final uri = Uri.parse(value);
        return await launchUrl(uri, mode: LaunchMode.externalApplication);
      } catch (_) {
        return false;
      }
    }

    if (await tryUrl(url)) {
      return true;
    }
    if (url.startsWith('sms:')) {
      return tryUrl(url.replaceFirst('sms:', 'smsto:'));
    }
    return false;
  }
}

class _BucketChip extends StatelessWidget {
  const _BucketChip({
    required this.label,
    required this.selected,
    required this.onTap,
  });

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

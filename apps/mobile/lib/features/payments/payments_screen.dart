import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final paymentsProvider = FutureProvider.autoDispose<List<PaymentRecord>>((ref) {
  ref.watch(workspaceClockProvider);
  return ref.watch(paymentApiProvider).list();
});

class PaymentsScreen extends ConsumerWidget {
  const PaymentsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(paymentsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Receipts')),
      body: async.when(
        loading: () => const FsLoading(),
        error: (e, _) => FsErrorState(
          message: problemOf(e).detail,
          onRetry: () => ref.invalidate(paymentsProvider),
        ),
        data: (rows) {
          if (rows.isEmpty) {
            return const FsEmptyState(
              title: 'No receipts yet',
              message: 'Collect a fee from Pending to print a receipt number.',
            );
          }
          return ListView.separated(
            itemCount: rows.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (context, i) {
              final p = rows[i];
              return ListTile(
                title: Text('${p.receiptNo} · ${p.customerName}'),
                subtitle: Text(
                  [
                    p.method,
                    p.paidOn,
                    if (p.referenceNo != null && p.referenceNo!.isNotEmpty) p.referenceNo!,
                    if (p.isVoid) p.voidReason ?? 'VOID',
                  ].join(' · '),
                ),
                trailing: Text(
                  p.amountLabel,
                  style: TextStyle(
                    fontWeight: FontWeight.w700,
                    decoration: p.isVoid ? TextDecoration.lineThrough : null,
                  ),
                ),
                onTap: () => _showReceipt(context, ref, p),
              );
            },
          );
        },
      ),
    );
  }

  Future<void> _showReceipt(BuildContext context, WidgetRef ref, PaymentRecord p) async {
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(p.receiptNo),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Center(child: DueMateLogo(height: 48)),
            const SizedBox(height: 8),
            Text(ref.read(tenantConfigProvider)?.tenant?.title ?? ''),
            const SizedBox(height: 12),
            Text(
              '${p.customerName} (${p.customerCode})\n'
              '${p.amountLabel} · ${p.method}\n'
              'Paid ${p.paidOn}'
              '${p.referenceNo == null || p.referenceNo!.isEmpty ? '' : '\nRef ${p.referenceNo}'}'
              '${p.isVoid ? '\nVOID · ${p.voidReason ?? ''}' : ''}',
            ),
          ],
        ),
        actions: [
          if (!p.isVoid)
            PermissionGate(
              permission: 'payments.void',
              child: TextButton(
                onPressed: () async {
                  Navigator.pop(ctx);
                  await _void(context, ref, p);
                },
                child: const Text('Void'),
              ),
            ),
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Close')),
        ],
      ),
    );
  }

  Future<void> _void(BuildContext context, WidgetRef ref, PaymentRecord p) async {
    final reason = await showDialog<String>(
      context: context,
      builder: (ctx) {
        final controller = TextEditingController();
        return AlertDialog(
          title: const Text('Void receipt'),
          content: TextField(
            controller: controller,
            decoration: const InputDecoration(labelText: 'Reason'),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(ctx, controller.text), child: const Text('Void')),
          ],
        );
      },
    );
    if (reason == null) {
      return;
    }
    try {
      await ref.read(paymentApiProvider).voidPayment(p.id, reason: reason);
      ref.invalidate(paymentsProvider);
      tickWorkspace(ref);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('${p.receiptNo} voided')));
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(problemOf(e).detail)));
      }
    }
  }
}

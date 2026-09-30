import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/util/files.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final reportOverviewProvider = FutureProvider.autoDispose<ReportOverview>((ref) {
  ref.watch(workspaceClockProvider);
  return ref.watch(reportApiProvider).overview();
});

class ReportsScreen extends ConsumerWidget {
  const ReportsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(reportOverviewProvider);
    return Scaffold(
      appBar: AppBar(
        leading: const Padding(padding: EdgeInsets.all(6), child: DueMateLogo(height: 36)),
        title: const Text('Reports'),
        actions: [
          IconButton(
            tooltip: 'Export Excel',
            onPressed: () => _export(context, ref, 'xlsx'),
            icon: const Icon(Icons.ios_share_outlined),
          ),
        ],
      ),
      body: async.when(
        loading: () => const FsLoading(),
        error: (e, _) => FsErrorState(
          message: problemOf(e).detail,
          onRetry: () => ref.invalidate(reportOverviewProvider),
        ),
        data: (report) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(reportOverviewProvider),
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
            children: [
              FsEnter(
                child: _HeroCard(
                  title: 'Collected this month',
                  value: report.collectedMonthLabel,
                  caption:
                      'Fees ${report.feesMonthLabel} · Extras ${report.extrasMonthLabel}\nToday ${report.collectedTodayLabel} (fees ${report.feesTodayLabel} · extras ${report.extrasTodayLabel})',
                ),
              ),
              const SizedBox(height: 14),
              FsEnter(
                delay: const Duration(milliseconds: 60),
                child: _OverdueBar(report: report),
              ),
              const SizedBox(height: 14),
              LayoutBuilder(
                builder: (context, constraints) {
                  final gap = 10.0;
                  final width = (constraints.maxWidth - gap) / 2;
                  final tiles = [
                    _KpiTile(
                      label: 'Active members',
                      value: '${report.members}',
                      icon: Icons.groups_outlined,
                      tone: FsTone.success,
                    ),
                    _KpiTile(
                      label: 'Open dues',
                      value: '${report.pendingCount}',
                      icon: Icons.schedule_outlined,
                      tone: FsTone.warning,
                    ),
                    _KpiTile(
                      label: 'Outstanding',
                      value: report.outstandingLabel,
                      icon: Icons.account_balance_wallet_outlined,
                      tone: FsTone.neutral,
                    ),
                    _KpiTile(
                      label: 'Overdue',
                      value: '${report.overdueCount}',
                      caption: report.overdueLabel,
                      icon: Icons.warning_amber_rounded,
                      tone: FsTone.danger,
                    ),
                    _KpiTile(
                      label: 'Fees this month',
                      value: report.feesMonthLabel,
                      icon: Icons.payments_outlined,
                      tone: FsTone.success,
                    ),
                    _KpiTile(
                      label: 'Extras this month',
                      value: report.extrasMonthLabel,
                      caption: 'Today ${report.extrasTodayLabel}',
                      icon: Icons.shopping_bag_outlined,
                      tone: FsTone.neutral,
                    ),
                  ];
                  return Wrap(
                    spacing: gap,
                    runSpacing: gap,
                    children: [
                      for (var i = 0; i < tiles.length; i++)
                        SizedBox(
                          width: width,
                          child: FsEnter(
                            delay: Duration(milliseconds: 80 + (i * 40)),
                            child: tiles[i],
                          ),
                        ),
                    ],
                  );
                },
              ),
              const SizedBox(height: 24),
              FsEnter(
                delay: const Duration(milliseconds: 180),
                child: Text('Outstanding by plan', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
              ),
              const SizedBox(height: 10),
              if (report.byPlan.isEmpty)
                const FsEnter(
                  delay: Duration(milliseconds: 200),
                  child: FsCard(
                    child: Padding(
                      padding: EdgeInsets.all(18),
                      child: Text('No open dues.'),
                    ),
                  ),
                )
              else
                FsEnter(
                  delay: const Duration(milliseconds: 200),
                  child: FsCard(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                      child: Column(
                        children: [
                          for (final plan in report.byPlan)
                            _PlanRow(
                              plan: plan,
                              maxMinor: report.byPlan
                                  .map((p) => p.outstandingMinor)
                                  .fold<int>(1, (a, b) => a > b ? a : b),
                            ),
                        ],
                      ),
                    ),
                  ),
                ),
              const SizedBox(height: 24),
              FsEnter(
                delay: const Duration(milliseconds: 240),
                child: Text('All collections', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
              ),
              const SizedBox(height: 4),
              Text(
                'Membership fees and extras in one list so you can monitor every payment.',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const SizedBox(height: 10),
              _PaymentMonitor(payments: report.recentPayments),
            ],
          ),
        ),
      ),
    );
  }
}

Future<void> _export(BuildContext context, WidgetRef ref, String format) async {
  try {
    final file = await ref.read(reportApiProvider).export(format: format);
    final path = await saveBytes(file.filename, file.bytes);
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Saved $path')));
    }
  } catch (e) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
    }
  }
}

class _PaymentMonitor extends StatefulWidget {
  const _PaymentMonitor({required this.payments});

  final List<RecentPayment> payments;

  @override
  State<_PaymentMonitor> createState() => _PaymentMonitorState();
}

class _PaymentMonitorState extends State<_PaymentMonitor> {
  String _filter = 'ALL';

  @override
  Widget build(BuildContext context) {
    final items = widget.payments.where((p) {
      if (_filter == 'FEE') return !p.isExtra;
      if (_filter == 'EXTRA') return p.isExtra;
      return true;
    }).toList();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Wrap(
          spacing: 8,
          children: [
            FilterChip(
              label: const Text('All'),
              selected: _filter == 'ALL',
              onSelected: (_) => setState(() => _filter = 'ALL'),
            ),
            FilterChip(
              label: const Text('Fees'),
              selected: _filter == 'FEE',
              onSelected: (_) => setState(() => _filter = 'FEE'),
            ),
            FilterChip(
              label: const Text('Extras'),
              selected: _filter == 'EXTRA',
              onSelected: (_) => setState(() => _filter = 'EXTRA'),
            ),
          ],
        ),
        const SizedBox(height: 10),
        if (items.isEmpty)
          const FsCard(
            child: Padding(
              padding: EdgeInsets.all(18),
              child: Text('No collections in this view yet.'),
            ),
          )
        else
          for (var i = 0; i < items.length; i++)
            Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: FsCard(
                child: ListTile(
                  leading: CircleAvatar(
                    backgroundColor: items[i].isExtra
                        ? Theme.of(context).colorScheme.tertiaryContainer
                        : Theme.of(context).colorScheme.primaryContainer,
                    child: Icon(
                      items[i].isExtra ? Icons.shopping_bag_outlined : Icons.south_west,
                      color: items[i].isExtra
                          ? Theme.of(context).colorScheme.onTertiaryContainer
                          : Theme.of(context).colorScheme.onPrimaryContainer,
                      size: 18,
                    ),
                  ),
                  title: Text(items[i].customerName, style: const TextStyle(fontWeight: FontWeight.w700)),
                  subtitle: Text(
                    [
                      items[i].isExtra ? 'Extra' : 'Fee',
                      if (items[i].category.isNotEmpty) items[i].category,
                      if (items[i].branchName.isNotEmpty) items[i].branchName,
                      items[i].receiptNo,
                      items[i].method,
                      items[i].paidOn,
                    ].join(' · '),
                  ),
                  trailing: Text(
                    items[i].amountLabel,
                    style: const TextStyle(fontWeight: FontWeight.w800),
                  ),
                ),
              ),
            ),
      ],
    );
  }
}

class _HeroCard extends StatelessWidget {
  const _HeroCard({required this.title, required this.value, required this.caption});

  final String title;
  final String value;
  final String caption;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(20, 22, 20, 20),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(24),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [scheme.primary, scheme.tertiary],
        ),
        boxShadow: [
          BoxShadow(color: scheme.primary.withValues(alpha: 0.28), blurRadius: 22, offset: const Offset(0, 10)),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: TextStyle(color: scheme.onPrimary.withValues(alpha: 0.9), fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          Text(
            value,
            style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                  color: scheme.onPrimary,
                  fontWeight: FontWeight.w800,
                ),
          ),
          const SizedBox(height: 6),
          Text(caption, style: TextStyle(color: scheme.onPrimary.withValues(alpha: 0.85))),
        ],
      ),
    );
  }
}

class _OverdueBar extends StatelessWidget {
  const _OverdueBar({required this.report});

  final ReportOverview report;

  @override
  Widget build(BuildContext context) {
    final total = report.outstandingMinor <= 0 ? 1 : report.outstandingMinor;
    final share = (report.overdueMinor / total).clamp(0.0, 1.0);
    return FsCard(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Expanded(child: Text('Overdue share of outstanding', style: TextStyle(fontWeight: FontWeight.w700))),
                Text('${(share * 100).round()}%'),
              ],
            ),
            const SizedBox(height: 10),
            ClipRRect(
              borderRadius: BorderRadius.circular(999),
              child: TweenAnimationBuilder<double>(
                tween: Tween(begin: 0, end: share),
                duration: const Duration(milliseconds: 700),
                curve: Curves.easeOutCubic,
                builder: (context, value, _) {
                  return LinearProgressIndicator(
                    value: value,
                    minHeight: 10,
                    backgroundColor: Theme.of(context).colorScheme.surfaceContainerHighest,
                    color: Theme.of(context).colorScheme.error,
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _KpiTile extends StatelessWidget {
  const _KpiTile({
    required this.label,
    required this.value,
    required this.icon,
    required this.tone,
    this.caption,
  });

  final String label;
  final String value;
  final String? caption;
  final IconData icon;
  final FsTone tone;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final color = switch (tone) {
      FsTone.success => scheme.primary,
      FsTone.warning => const Color(0xFFB45309),
      FsTone.danger => scheme.error,
      FsTone.neutral => scheme.onSurface,
    };
    return FsCard(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 14, 14, 14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, size: 20, color: color),
            const SizedBox(height: 10),
            Text(label, style: Theme.of(context).textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant)),
            const SizedBox(height: 4),
            Text(value, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
            if (caption != null) ...[
              const SizedBox(height: 2),
              Text(caption!, style: Theme.of(context).textTheme.bodySmall),
            ],
          ],
        ),
      ),
    );
  }
}

class _PlanRow extends StatelessWidget {
  const _PlanRow({required this.plan, required this.maxMinor});

  final PlanBreakdown plan;
  final int maxMinor;

  @override
  Widget build(BuildContext context) {
    final fraction = maxMinor <= 0 ? 0.0 : (plan.outstandingMinor / maxMinor).clamp(0.0, 1.0);
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Text(plan.name, style: const TextStyle(fontWeight: FontWeight.w600))),
              Text(plan.outstandingLabel, style: const TextStyle(fontWeight: FontWeight.w800)),
            ],
          ),
          const SizedBox(height: 4),
          Text('${plan.feeCount} open', style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: 8),
          ClipRRect(
            borderRadius: BorderRadius.circular(999),
            child: TweenAnimationBuilder<double>(
              tween: Tween(begin: 0, end: fraction),
              duration: const Duration(milliseconds: 650),
              curve: Curves.easeOutCubic,
              builder: (context, value, _) {
                return LinearProgressIndicator(
                  value: value,
                  minHeight: 8,
                  backgroundColor: scheme.surfaceContainerHighest,
                  color: scheme.primary,
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

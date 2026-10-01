import 'dart:convert';
import 'dart:typed_data';

import 'package:feesaas_core/src/config/tenant_config.dart';
import 'package:feesaas_core/src/ui/hat_loader.dart';
import 'package:feesaas_core/src/ui/motion.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class FsStatusChip extends StatelessWidget {
  const FsStatusChip({super.key, required this.label, this.tone = FsTone.neutral});

  final String label;
  final FsTone tone;

  @override
  Widget build(BuildContext context) {
    final colors = switch (tone) {
      FsTone.success => (const Color(0xFFDCFCE7), const Color(0xFF166534)),
      FsTone.warning => (const Color(0xFFFEF3C7), const Color(0xFF92400E)),
      FsTone.danger => (const Color(0xFFFEE2E2), const Color(0xFF991B1B)),
      FsTone.neutral => (const Color(0xFFE5E7EB), const Color(0xFF374151)),
    };
    return AnimatedContainer(
      duration: const Duration(milliseconds: 250),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(color: colors.$1, borderRadius: BorderRadius.circular(999)),
      child: Text(label, style: TextStyle(color: colors.$2, fontWeight: FontWeight.w600, fontSize: 12)),
    );
  }
}

enum FsTone { success, warning, danger, neutral }

class FsEmptyState extends StatelessWidget {
  const FsEmptyState({super.key, required this.title, this.message, this.action});

  final String title;
  final String? message;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Center(
      child: FsEnter(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.inbox_outlined, size: 48, color: scheme.primary),
              const SizedBox(height: 16),
              Text(title, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700), textAlign: TextAlign.center),
              if (message != null) ...[
                const SizedBox(height: 8),
                Text(message!, textAlign: TextAlign.center, style: TextStyle(color: scheme.onSurfaceVariant)),
              ],
              if (action != null) ...[const SizedBox(height: 16), action!],
            ],
          ),
        ),
      ),
    );
  }
}

class FsErrorState extends StatelessWidget {
  const FsErrorState({super.key, required this.message, this.onRetry});

  final String message;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    return FsEmptyState(
      title: 'Something went wrong',
      message: message,
      action: onRetry == null ? null : FilledButton(onPressed: onRetry, child: const Text('Retry')),
    );
  }
}

class FsLoading extends ConsumerWidget {
  const FsLoading({super.key, this.personal});

  final bool? personal;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final piggy = personal ?? ref.watch(tenantConfigProvider)?.isPersonal ?? true;
    return Center(child: HatOrbitLoader(personal: piggy));
  }
}

Future<T?> showFsSheet<T>({
  required BuildContext context,
  required WidgetBuilder builder,
}) {
  return showModalBottomSheet<T>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    useSafeArea: true,
    builder: (ctx) {
      final media = MediaQuery.of(ctx);
      return Padding(
        padding: EdgeInsets.only(
          left: 20,
          right: 20,
          top: 4,
          bottom: media.viewInsets.bottom + 16,
        ),
        child: ConstrainedBox(
          constraints: BoxConstraints(maxHeight: media.size.height * 0.86),
          child: builder(ctx),
        ),
      );
    },
  );
}

class FsSheetForm extends StatelessWidget {
  const FsSheetForm({
    super.key,
    required this.title,
    this.actions = const [],
    required this.body,
  });

  final String title;
  final Widget body;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(title, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
          const SizedBox(height: 12),
          body,
          if (actions.isNotEmpty) ...[
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: actions,
            ),
          ],
        ],
      ),
    );
  }
}

class DueMateLogo extends ConsumerWidget {
  const DueMateLogo({super.key, this.height = 160});

  static const asset = AssetImage('assets/duemate_logo.png', package: 'feesaas_core');

  final double height;

  static Uint8List? decodeBytes(String? raw) {
    if (raw == null || raw.isEmpty) {
      return null;
    }
    try {
      final payload = raw.contains(',') ? raw.split(',').last : raw;
      final bytes = base64Decode(payload);
      return bytes.isEmpty ? null : bytes;
    } catch (_) {
      return null;
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tenantBytes = decodeBytes(ref.watch(tenantConfigProvider)?.tenant?.logoBase64);
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0.92, end: 1),
      duration: const Duration(milliseconds: 700),
      curve: Curves.easeOutBack,
      builder: (context, scale, child) => Transform.scale(scale: scale, child: child),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: const Color(0xFF0B0B0B),
          borderRadius: BorderRadius.circular(20),
          boxShadow: [
            BoxShadow(
              color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.18),
              blurRadius: 24,
              offset: const Offset(0, 10),
            ),
          ],
        ),
        child: Padding(
          padding: EdgeInsets.all(height < 48 ? 4 : 12),
          child: tenantBytes != null
              ? Image.memory(tenantBytes, height: height, fit: BoxFit.contain)
              : Image(image: asset, height: height, fit: BoxFit.contain),
        ),
      ),
    );
  }
}

class FsAuthScaffold extends StatelessWidget {
  const FsAuthScaffold({
    super.key,
    required this.title,
    required this.subtitle,
    required this.children,
    this.busy = false,
    this.busyLabel = 'Signing in…',
  });

  final String title;
  final String subtitle;
  final List<Widget> children;
  final bool busy;
  final String busyLabel;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      body: Stack(
        children: [
          DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [
                  scheme.primaryContainer.withValues(alpha: 0.55),
                  scheme.surface,
                  scheme.secondaryContainer.withValues(alpha: 0.35),
                ],
              ),
            ),
            child: SafeArea(
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 440),
                  child: ListView(
                    padding: const EdgeInsets.fromLTRB(24, 32, 24, 24),
                    children: [
                      FsEnter(child: Center(child: DueMateLogo(height: 120))),
                      const SizedBox(height: 28),
                      FsEnter(
                        delay: const Duration(milliseconds: 80),
                        child: Text(
                          title,
                          textAlign: TextAlign.center,
                          style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800),
                        ),
                      ),
                      const SizedBox(height: 8),
                      FsEnter(
                        delay: const Duration(milliseconds: 140),
                        child: Text(
                          subtitle,
                          textAlign: TextAlign.center,
                          style: Theme.of(context).textTheme.bodyLarge?.copyWith(color: scheme.onSurfaceVariant),
                        ),
                      ),
                      const SizedBox(height: 28),
                      FsEnter(
                        delay: const Duration(milliseconds: 200),
                        child: Card(
                          child: Padding(
                            padding: const EdgeInsets.fromLTRB(20, 24, 20, 20),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: children,
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          if (busy)
            Positioned.fill(
              child: ColoredBox(
                color: Colors.black.withValues(alpha: 0.38),
                child: Center(
                  child: Card(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(28, 24, 28, 24),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          HatOrbitLoader(personal: true, size: 120),
                          const SizedBox(height: 8),
                          Text(
                            busyLabel,
                            style: Theme.of(context).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class FsUnreadBadge extends StatelessWidget {
  const FsUnreadBadge({super.key, required this.count, required this.child});

  final int count;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    if (count <= 0) {
      return child;
    }
    return Badge(
      isLabelVisible: true,
      backgroundColor: Theme.of(context).colorScheme.error,
      textColor: Theme.of(context).colorScheme.onError,
      label: Text(count > 99 ? '99+' : '$count'),
      child: child,
    );
  }
}

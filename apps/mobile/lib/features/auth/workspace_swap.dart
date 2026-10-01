import 'dart:ui';

import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

bool _swapLock = false;

Future<void> swapWorkspace(BuildContext context, WidgetRef ref, UserContext next) async {
  if (_swapLock) {
    return;
  }
  _swapLock = true;
  final toMoney = next.kind == 'PERSONAL';
  final overlay = Overlay.of(context, rootOverlay: true);
  late OverlayEntry entry;
  entry = OverlayEntry(
    builder: (_) => WorkspaceSwapLoader(toMoney: toMoney),
  );
  overlay.insert(entry);
  final started = DateTime.now();
  Object? error;
  try {
    await ref.read(sessionControllerProvider.notifier).switchContext(next);
    if (context.mounted) {
      final home = ref.read(tenantConfigProvider)?.homeLocation ?? (toMoney ? '/personal' : '/pending');
      context.go(home);
    }
  } catch (e) {
    error = e;
  }
  final wait = const Duration(milliseconds: 1200) - DateTime.now().difference(started);
  if (wait > Duration.zero) {
    await Future<void>.delayed(wait);
  }
  entry.remove();
  _swapLock = false;
  if (error != null && context.mounted) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(problemOf(error).detail)));
  }
}

class WorkspaceSwapLoader extends StatelessWidget {
  const WorkspaceSwapLoader({super.key, required this.toMoney});

  final bool toMoney;

  @override
  Widget build(BuildContext context) {
    return Positioned.fill(
      child: AbsorbPointer(
        child: Stack(
          fit: StackFit.expand,
          children: [
            BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
              child: const ColoredBox(color: Color(0xE80B1F1C)),
            ),
            Center(child: HatOrbitLoader(personal: toMoney, flip: true, size: 200)),
          ],
        ),
      ),
    );
  }
}

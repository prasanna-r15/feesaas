import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class SuspendedScreen extends ConsumerWidget {
  const SuspendedScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      body: FsEmptyState(
        title: 'This business is suspended',
        message: 'Ask your DueMate admin to activate the account, then sign in again.',
        action: FilledButton(
          onPressed: () => ref.read(sessionControllerProvider.notifier).logout(),
          child: const Text('Sign out'),
        ),
      ),
    );
  }
}

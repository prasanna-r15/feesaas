import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Bumped after collects, imports, and tab switches so IndexedStack tabs refetch.
final workspaceClockProvider = StateProvider<int>((ref) => 0);

void tickWorkspace(WidgetRef ref) {
  ref.read(workspaceClockProvider.notifier).state++;
}

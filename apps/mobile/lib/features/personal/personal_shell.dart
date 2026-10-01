import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class PersonalShell extends ConsumerWidget {
  const PersonalShell({super.key, required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final unread = ref.watch(mySupportUnreadProvider);
    return Scaffold(
      body: navigationShell,
      bottomNavigationBar: NavigationBar(
        selectedIndex: navigationShell.currentIndex,
        destinations: [
          const NavigationDestination(icon: Icon(Icons.home_outlined), label: 'Home'),
          const NavigationDestination(icon: Icon(Icons.receipt_long_outlined), label: 'Money'),
          const NavigationDestination(icon: Icon(Icons.pie_chart_outline), label: 'Budget'),
          const NavigationDestination(icon: Icon(Icons.groups_outlined), label: 'Groups'),
          NavigationDestination(
            icon: FsUnreadBadge(count: unread, child: const Icon(Icons.person_outline)),
            label: 'Profile',
          ),
        ],
        onDestinationSelected: navigationShell.goBranch,
      ),
    );
  }
}

import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class AdminScaffold extends ConsumerStatefulWidget {
  const AdminScaffold({
    super.key,
    required this.title,
    required this.body,
    this.floatingActionButton,
  });

  final String title;
  final Widget body;
  final Widget? floatingActionButton;

  static const links = <({String label, String path, IconData icon})>[
    (label: 'Tenants', path: '/tenants', icon: Icons.apartment_outlined),
    (label: 'Join requests', path: '/join-enquiries', icon: Icons.mail_outline),
    (label: 'Chat', path: '/support', icon: Icons.chat_outlined),
    (label: 'Individuals', path: '/individuals', icon: Icons.people_outline),
    (label: 'Dues email', path: '/mail', icon: Icons.mark_email_unread_outlined),
    (label: 'Config', path: '/config', icon: Icons.tune_outlined),
  ];

  @override
  ConsumerState<AdminScaffold> createState() => _AdminScaffoldState();
}

class _AdminScaffoldState extends ConsumerState<AdminScaffold> {
  @override
  Widget build(BuildContext context) {
    final here = GoRouterState.of(context).uri.path;
    final unread = ref.watch(platformSupportUnreadProvider);
    ref.listen<int>(platformSupportUnreadProvider, (prev, next) {
      if (!mounted || prev == null || next <= prev || next <= 0) {
        return;
      }
      if (here.startsWith('/support')) {
        return;
      }
      final messenger = ScaffoldMessenger.of(context);
      messenger.clearSnackBars();
      messenger.showSnackBar(
        SnackBar(
          content: Text(next == 1 ? 'New support message' : '$next unread support messages'),
          action: SnackBarAction(label: 'Open', onPressed: () => context.go('/support')),
        ),
      );
    });
    return Scaffold(
      appBar: AppBar(
        leading: const Padding(padding: EdgeInsets.all(6), child: DueMateLogo(height: 36)),
        title: Text(widget.title, overflow: TextOverflow.ellipsis),
        actions: [
          const AdminChatAction(),
          Builder(
            builder: (ctx) => IconButton(
              tooltip: 'Menu',
              icon: const Icon(Icons.menu),
              onPressed: () => Scaffold.of(ctx).openEndDrawer(),
            ),
          ),
        ],
      ),
      endDrawer: Drawer(
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.symmetric(vertical: 8),
            children: [
              const ListTile(
                title: Text('DueMate admin', style: TextStyle(fontWeight: FontWeight.w800)),
                subtitle: Text('Platform'),
              ),
              const Divider(),
              for (final link in AdminScaffold.links)
                ListTile(
                  leading: link.path == '/support'
                      ? FsUnreadBadge(count: unread, child: Icon(link.icon))
                      : Icon(link.icon),
                  title: Text(link.label),
                  selected: here == link.path || here.startsWith('${link.path}/'),
                  onTap: () => context.go(link.path),
                ),
              const Divider(),
              ListTile(
                leading: const Icon(Icons.logout),
                title: const Text('Sign out'),
                onTap: () {
                  ref.read(sessionControllerProvider.notifier).logout();
                },
              ),
            ],
          ),
        ),
      ),
      floatingActionButton: widget.floatingActionButton,
      body: widget.body,
    );
  }
}

class AdminChatAction extends ConsumerWidget {
  const AdminChatAction({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final unread = ref.watch(platformSupportUnreadProvider);
    return IconButton(
      tooltip: unread > 0 ? '$unread unread' : 'Chat',
      onPressed: () => context.go('/support'),
      icon: FsUnreadBadge(count: unread, child: const Icon(Icons.chat_outlined)),
    );
  }
}

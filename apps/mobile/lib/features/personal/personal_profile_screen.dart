import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class PersonalProfileScreen extends ConsumerWidget {
  const PersonalProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(tenantConfigProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Profile')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(config?.user.fullName ?? '', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)),
          const SizedBox(height: 16),
          if ((config?.bootstrap.contexts.length ?? 0) > 1) ...[
            const Text('Switch to', style: TextStyle(fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            for (final c in config!.bootstrap.contexts)
              FsCard(
                child: ListTile(
                  title: Text(c.label),
                  subtitle: Text(c.kind),
                  selected: c.kind == config.bootstrap.activeContext?.kind && c.groupId == config.bootstrap.activeContext?.groupId,
                  onTap: () async {
                    await ref.read(sessionControllerProvider.notifier).switchContext(c);
                    if (context.mounted) context.go(ref.read(tenantConfigProvider)?.homeLocation ?? '/personal');
                  },
                ),
              ),
            const SizedBox(height: 16),
          ],
          FsCard(
            child: ListTile(
              leading: const Icon(Icons.logout),
              title: const Text('Sign out'),
              onTap: () => ref.read(sessionControllerProvider.notifier).logout(),
            ),
          ),
        ],
      ),
    );
  }
}

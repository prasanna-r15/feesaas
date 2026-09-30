import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class OnboardingScreen extends ConsumerWidget {
  const OnboardingScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      appBar: AppBar(title: const Text('Welcome')),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          Text('What do you want to do?', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
          const SizedBox(height: 20),
          FsCard(
            child: ListTile(
              leading: const Icon(Icons.savings_outlined),
              title: const Text('Manage my money'),
              subtitle: const Text('Expenses, income, and budgets'),
              onTap: () async {
                await ref.read(sessionControllerProvider.notifier).completeOnboarding();
                if (context.mounted) context.go('/personal');
              },
            ),
          ),
          const SizedBox(height: 12),
          FsCard(
            child: ListTile(
              leading: const Icon(Icons.groups_outlined),
              title: const Text('Join a group'),
              subtitle: const Text('Split a trip, dinner, or rent'),
              onTap: () async {
                await ref.read(sessionControllerProvider.notifier).completeOnboarding();
                if (context.mounted) context.go('/groups');
              },
            ),
          ),
          const SizedBox(height: 12),
          FsCard(
            child: ListTile(
              leading: const Icon(Icons.storefront_outlined),
              title: const Text('Join a business'),
              subtitle: const Text('Ask your admin to add this email as staff'),
              onTap: () async {
                await ref.read(sessionControllerProvider.notifier).completeOnboarding();
                if (context.mounted) context.go('/personal');
              },
            ),
          ),
          const SizedBox(height: 24),
          TextButton(
            onPressed: () async {
              await ref.read(sessionControllerProvider.notifier).completeOnboarding();
              if (context.mounted) context.go('/personal');
            },
            child: const Text('Skip for now'),
          ),
        ],
      ),
    );
  }
}

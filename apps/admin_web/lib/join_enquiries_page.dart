import 'package:feesaas_admin_web/admin_app_bar.dart';
import 'package:feesaas_admin_web/slug.dart';
import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

final joinEnquiriesProvider = FutureProvider.autoDispose<List<JoinEnquiry>>((ref) {
  return ref.watch(platformApiProvider).listJoinEnquiries();
});

class JoinEnquiriesPage extends ConsumerWidget {
  const JoinEnquiriesPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(joinEnquiriesProvider);
    return AdminScaffold(
      title: 'Join requests',
      body: async.when(
        loading: () => const FsLoading(personal: false),
        error: (e, _) => FsErrorState(
          message: problemOf(e).detail,
          onRetry: () => ref.invalidate(joinEnquiriesProvider),
        ),
        data: (rows) {
          if (rows.isEmpty) {
            return const FsEmptyState(
              title: 'No join requests',
              message: 'When someone asks to open their gym, yoga studio, or other business, it lands here.',
            );
          }
          return ListView.separated(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
            itemCount: rows.length,
            separatorBuilder: (_, __) => const SizedBox(height: 10),
            itemBuilder: (context, i) {
              final r = rows[i];
              return FsCard(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(8, 8, 8, 12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      ListTile(
                        title: Text(r.businessName, style: const TextStyle(fontWeight: FontWeight.w700)),
                        subtitle: Text(
                          [
                            r.fullName,
                            r.email ?? '',
                            r.phone ?? '',
                            r.city ?? '',
                            r.status,
                            if (r.createdAt != null && r.createdAt!.isNotEmpty) formatLocalDateTime(r.createdAt),
                            if (r.message != null && r.message!.isNotEmpty) r.message!,
                          ].where((e) => e.isNotEmpty).join(' · '),
                        ),
                        isThreeLine: true,
                      ),
                      Padding(
                        padding: const EdgeInsets.fromLTRB(12, 0, 12, 0),
                        child: Wrap(
                          spacing: 8,
                          children: [
                            OutlinedButton.icon(
                              onPressed: () async {
                                try {
                                  final thread = await ref.read(platformApiProvider).openSupportThread(r.userId);
                                  if (context.mounted) {
                                    context.push('/support/${thread.id}');
                                  }
                                } catch (e) {
                                  if (context.mounted) {
                                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(problemOf(e).detail)));
                                  }
                                }
                              },
                              icon: const Icon(Icons.chat_outlined, size: 18),
                              label: const Text('Chat'),
                            ),
                            if (r.status != 'CLOSED')
                              FilledButton.icon(
                                onPressed: () => _approve(context, ref, r),
                                icon: const Icon(Icons.storefront_outlined, size: 18),
                                label: const Text('Create their business'),
                              ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              );
            },
          );
        },
      ),
    );
  }
}

Future<void> _approve(BuildContext context, WidgetRef ref, JoinEnquiry enquiry) async {
  final nameCtrl = TextEditingController(text: enquiry.businessName);
  final slugCtrl = TextEditingController(text: slugify(enquiry.businessName));
  var type = 'GYM';
  var slugEdited = false;
  nameCtrl.addListener(() {
    if (!slugEdited) {
      slugCtrl.text = slugify(nameCtrl.text);
    }
  });
  final ok = await showDialog<bool>(
    context: context,
    builder: (ctx) => StatefulBuilder(
      builder: (ctx, setLocal) => AlertDialog(
        title: Text('Open a business for ${enquiry.fullName}'),
        content: SizedBox(
          width: 440,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('${enquiry.email ?? ''} · ${enquiry.phone ?? ''}'.trim()),
              const SizedBox(height: 8),
              const Text('This creates a new tenant. They become the owner and keep their existing login.'),
              const SizedBox(height: 12),
              TextField(
                controller: nameCtrl,
                decoration: const InputDecoration(labelText: 'Business name'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: slugCtrl,
                onChanged: (_) => slugEdited = true,
                decoration: const InputDecoration(labelText: 'URL slug'),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                value: type,
                items: const [
                  DropdownMenuItem(value: 'GYM', child: Text('Gym')),
                  DropdownMenuItem(value: 'GENERIC', child: Text('Yoga / studio / other')),
                  DropdownMenuItem(value: 'ACADEMY', child: Text('Academy')),
                  DropdownMenuItem(value: 'TUITION', child: Text('Tuition')),
                ],
                onChanged: (v) => setLocal(() => type = v ?? 'GYM'),
                decoration: const InputDecoration(labelText: 'Business type'),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Create')),
        ],
      ),
    ),
  );
  final name = nameCtrl.text.trim();
  final slug = slugify(slugCtrl.text.isEmpty ? name : slugCtrl.text);
  nameCtrl.dispose();
  slugCtrl.dispose();
  if (ok != true) {
    return;
  }
  if (name.isEmpty || slug.length < 2) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Enter a business name and a URL slug.')),
      );
    }
    return;
  }
  try {
    final created = await ref.read(platformApiProvider).approveJoinEnquiry(
          enquiry.id,
          name: name,
          slug: slug,
          businessType: type,
        );
    ref.invalidate(joinEnquiriesProvider);
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${enquiry.fullName} is now owner of ${created.name}.')),
      );
      if (created.tenantId.isNotEmpty) {
        context.push('/tenants/${created.tenantId}');
      }
    }
  } catch (e) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(problemOf(e).detail)));
    }
  }
}

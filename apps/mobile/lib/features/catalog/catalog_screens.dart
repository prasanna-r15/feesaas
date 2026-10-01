import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

final branchListProvider = FutureProvider.autoDispose((ref) {
  ref.watch(workspaceClockProvider);
  return ref.watch(catalogApiProvider).listBranches();
});
final addonListProvider = FutureProvider.autoDispose((ref) {
  ref.watch(workspaceClockProvider);
  return ref.watch(catalogApiProvider).listAddons();
});
final addonSalesProvider = FutureProvider.autoDispose((ref) {
  ref.watch(workspaceClockProvider);
  return ref.watch(catalogApiProvider).listSales();
});
final dietListProvider = FutureProvider.autoDispose((ref) {
  ref.watch(workspaceClockProvider);
  return ref.watch(catalogApiProvider).listDiets();
});

class BranchesScreen extends ConsumerWidget {
  const BranchesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(branchListProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Locations')),
      floatingActionButton: PermissionGate(
        permission: 'settings.manage',
        child: FloatingActionButton.extended(
          onPressed: () => _addBranch(context, ref),
          label: const Text('Add location'),
          icon: const Icon(Icons.add),
        ),
      ),
      body: async.when(
        loading: () => const FsLoading(),
        error: (e, _) => FsErrorState(message: problemOf(e).detail, onRetry: () => ref.invalidate(branchListProvider)),
        data: (rows) {
          if (rows.isEmpty) {
            return const FsEmptyState(
              title: 'No locations yet',
              message: 'Add Karamadai, Teachers Colony, or any other branch this business operates.',
            );
          }
          return ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: rows.length,
            separatorBuilder: (_, __) => const SizedBox(height: 10),
            itemBuilder: (context, i) {
              final b = rows[i];
              return FsCard(
                child: ListTile(
                  title: Text(b.name, style: const TextStyle(fontWeight: FontWeight.w700)),
                  subtitle: Text([if (b.primary) 'Primary', b.address, b.phone].whereType<String>().where((s) => s.isNotEmpty).join(' · ')),
                  trailing: IconButton(
                    icon: const Icon(Icons.delete_outline),
                    onPressed: () async {
                      await ref.read(catalogApiProvider).deleteBranch(b.id);
                      ref.invalidate(branchListProvider);
                    },
                  ),
                ),
              );
            },
          );
        },
      ),
    );
  }

  Future<void> _addBranch(BuildContext context, WidgetRef ref) async {
    final name = TextEditingController();
    final address = TextEditingController();
    final ok = await showFsSheet<bool>(
      context: context,
      builder: (ctx) => FsSheetForm(
        title: 'New location',
        body: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(controller: name, decoration: const InputDecoration(labelText: 'Name (e.g. Karamadai)')),
            const SizedBox(height: 8),
            TextField(controller: address, decoration: const InputDecoration(labelText: 'Address')),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Save')),
        ],
      ),
    );
    if (ok == true && name.text.trim().isNotEmpty) {
      await ref.read(catalogApiProvider).createBranch(name: name.text.trim(), address: address.text.trim());
      ref.invalidate(branchListProvider);
    }
  }
}

class AddonsScreen extends ConsumerWidget {
  const AddonsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final products = ref.watch(addonListProvider);
    final sales = ref.watch(addonSalesProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Extras')),
      floatingActionButton: PermissionGate(
        permission: 'settings.manage',
        child: FloatingActionButton.extended(
          onPressed: () => _addProduct(context, ref),
          label: const Text('Add product'),
          icon: const Icon(Icons.add),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text('Collect protein, diet packs, and other extras without mixing them into membership fees.',
              style: Theme.of(context).textTheme.bodyMedium),
          const SizedBox(height: 16),
          products.when(
            loading: () => const LinearProgressIndicator(),
            error: (e, _) => Text(problemOf(e).detail),
            data: (rows) {
              if (rows.isEmpty) {
                return const Text('No products yet. Add Protein, Diet chart, or a custom extra.');
              }
              return Column(
                children: [
                  for (final p in rows)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: FsCard(
                        child: ListTile(
                          title: Text(p.name, style: const TextStyle(fontWeight: FontWeight.w700)),
                          subtitle: Text('${p.amountLabel}${p.description == null ? '' : ' · ${p.description}'}'),
                          trailing: Wrap(
                            children: [
                              TextButton(onPressed: () => _collect(context, ref, p), child: const Text('Collect')),
                              IconButton(
                                icon: const Icon(Icons.delete_outline),
                                onPressed: () async {
                                  await ref.read(catalogApiProvider).deleteAddon(p.id);
                                  ref.invalidate(addonListProvider);
                                },
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                ],
              );
            },
          ),
          const SizedBox(height: 16),
          Text('Recent collections', style: Theme.of(context).textTheme.titleMedium),
          sales.when(
            loading: () => const SizedBox.shrink(),
            error: (e, _) => Text(problemOf(e).detail),
            data: (rows) => Column(
              children: [
                for (final s in rows.take(20))
                  ListTile(
                    dense: true,
                    title: Text('${s.customerName} · ${s.productName}'),
                    subtitle: Text('${s.soldOn} · ${s.method}'),
                    trailing: Text('₹${(s.amountMinor / 100).toStringAsFixed(2)}'),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _addProduct(BuildContext context, WidgetRef ref) async {
    final name = TextEditingController();
    final rupees = TextEditingController();
    final ok = await showFsSheet<bool>(
      context: context,
      builder: (ctx) => FsSheetForm(
        title: 'New extra',
        body: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(controller: name, decoration: const InputDecoration(labelText: 'Name (e.g. Protein)')),
            const SizedBox(height: 8),
            TextField(controller: rupees, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Amount (₹)')),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Save')),
        ],
      ),
    );
    if (ok == true && name.text.trim().isNotEmpty) {
      final rupee = double.tryParse(rupees.text.trim()) ?? 0;
      await ref.read(catalogApiProvider).createAddon(name: name.text.trim(), amountMinor: (rupee * 100).round());
      ref.invalidate(addonListProvider);
    }
  }

  Future<void> _collect(BuildContext context, WidgetRef ref, AddonProduct product) async {
    final members = await ref.read(customerApiProvider).list();
    if (!context.mounted || members.isEmpty) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Add a member first.')));
      }
      return;
    }
    String? memberId = members.first.id;
    final ok = await showFsSheet<bool>(
      context: context,
      builder: (ctx) => FsSheetForm(
        title: 'Collect ${product.name}',
        body: DropdownButtonFormField<String>(
          value: memberId,
          items: [for (final m in members) DropdownMenuItem(value: m.id, child: Text(m.fullName))],
          onChanged: (v) => memberId = v,
          decoration: const InputDecoration(labelText: 'Member'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Collect')),
        ],
      ),
    );
    if (ok == true && memberId != null) {
      await ref.read(catalogApiProvider).collect(productId: product.id, customerId: memberId!);
      ref.invalidate(addonSalesProvider);
      tickWorkspace(ref);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Collected')));
      }
    }
  }
}

class DietChartsScreen extends ConsumerWidget {
  const DietChartsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(dietListProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Diet charts')),
      floatingActionButton: PermissionGate(
        permission: 'settings.manage',
        child: FloatingActionButton.extended(
          onPressed: () => _edit(context, ref, null),
          label: const Text('New chart'),
          icon: const Icon(Icons.add),
        ),
      ),
      body: async.when(
        loading: () => const FsLoading(),
        error: (e, _) => FsErrorState(message: problemOf(e).detail, onRetry: () => ref.invalidate(dietListProvider)),
        data: (rows) {
          if (rows.isEmpty) {
            return const FsEmptyState(
              title: 'No diet charts yet',
              message: 'Write a chart once, then send it on WhatsApp to a branch or selected members.',
            );
          }
          return ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: rows.length,
            separatorBuilder: (_, __) => const SizedBox(height: 10),
            itemBuilder: (context, i) {
              final d = rows[i];
              return FsCard(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 14, 8, 8),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(d.name, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
                      const SizedBox(height: 8),
                      DietDocumentView(html: d.body, maxLines: 6),
                      Align(
                        alignment: Alignment.centerRight,
                        child: Wrap(
                          children: [
                            TextButton.icon(
                              onPressed: () => _edit(context, ref, d),
                              icon: const Icon(Icons.edit_outlined),
                              label: const Text('Edit'),
                            ),
                            TextButton.icon(
                              onPressed: () => _send(context, ref, d),
                              icon: const Icon(Icons.chat_outlined),
                              label: const Text('WhatsApp'),
                            ),
                            IconButton(
                              tooltip: 'Delete',
                              onPressed: () async {
                                await ref.read(catalogApiProvider).deleteDiet(d.id);
                                ref.invalidate(dietListProvider);
                              },
                              icon: const Icon(Icons.delete_outline),
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

  Future<void> _edit(BuildContext context, WidgetRef ref, DietChart? existing) async {
    final name = TextEditingController(text: existing?.name ?? '');
    final body = TextEditingController(text: existing?.body ?? '');
    final ok = await showFsSheet<bool>(
      context: context,
      builder: (ctx) => FsSheetForm(
        title: existing == null ? 'Diet chart' : 'Edit diet chart',
        body: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(controller: name, decoration: const InputDecoration(labelText: 'Name (e.g. Fat loss week 1)')),
            const SizedBox(height: 12),
            DietEditor(controller: body),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Save')),
        ],
      ),
    );
    if (ok == true && name.text.trim().isNotEmpty && body.text.trim().isNotEmpty) {
      if (existing == null) {
        await ref.read(catalogApiProvider).createDiet(name: name.text.trim(), body: body.text.trim());
      } else {
        await ref.read(catalogApiProvider).patchDiet(existing.id, name: name.text.trim(), body: body.text.trim());
      }
      ref.invalidate(dietListProvider);
    }
  }

  Future<void> _send(BuildContext context, WidgetRef ref, DietChart chart) async {
    final members = await ref.read(customerApiProvider).list();
    final branches = await ref.read(catalogApiProvider).listBranches();
    if (!context.mounted || members.isEmpty) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Add a member first.')));
      }
      return;
    }
    String? branchId;
    final selected = <String>{};
    final ok = await showFsSheet<bool>(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setLocal) {
            final visible = members.where((m) => branchId == null || m.branchId == branchId).toList();
            return FsSheetForm(
              title: 'Send ${chart.name}',
              body: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (branches.isNotEmpty) ...[
                    LocationPickerField(
                      branches: branches,
                      value: branchId,
                      allLabel: 'Any location',
                      onChanged: (id) => setLocal(() {
                        branchId = id;
                        if (id != null) {
                          selected
                            ..clear()
                            ..addAll(members.where((m) => m.branchId == id).map((m) => m.id));
                        }
                      }),
                    ),
                    const SizedBox(height: 12),
                  ],
                  Row(
                    children: [
                      Text('Members (${selected.length})'),
                      const Spacer(),
                      TextButton(
                        onPressed: () => setLocal(() {
                          selected
                            ..clear()
                            ..addAll(visible.map((m) => m.id));
                        }),
                        child: const Text('Select all shown'),
                      ),
                    ],
                  ),
                  for (final m in visible)
                    CheckboxListTile(
                      dense: true,
                      value: selected.contains(m.id),
                      title: Text(m.fullName),
                      subtitle: Text([m.branchName, m.phone].whereType<String>().where((s) => s.isNotEmpty).join(' · ')),
                      onChanged: (on) => setLocal(() {
                        if (on == true) {
                          selected.add(m.id);
                        } else {
                          selected.remove(m.id);
                        }
                      }),
                    ),
                ],
              ),
              actions: [
                TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
                FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Prepare WhatsApp')),
              ],
            );
          },
        );
      },
    );
    if (ok != true || (selected.isEmpty && branchId == null)) {
      return;
    }
    try {
      final sends = await ref.read(catalogApiProvider).sendDiet(
            templateId: chart.id,
            customerIds: selected.toList(),
            branchId: selected.isEmpty ? branchId : null,
          );
      if (!context.mounted) {
        return;
      }
      await showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: Text('Open WhatsApp (${sends.length})'),
          content: SizedBox(
            width: 420,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                for (final payload in sends)
                  ListTile(
                    title: Text(payload.memberName ?? 'Member'),
                    trailing: TextButton(
                      onPressed: () => launchUrl(
                        Uri.parse(payload.preferWhatsapp ? payload.waLink : payload.smsLink),
                        mode: LaunchMode.externalApplication,
                      ),
                      child: const Text('Open'),
                    ),
                  ),
              ],
            ),
          ),
          actions: [TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Done'))],
        ),
      );
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(problemOf(e).detail)));
      }
    }
  }
}

class BranchFilterBar extends ConsumerWidget {
  const BranchFilterBar({super.key, required this.value, required this.onChanged});

  final String? value;
  final ValueChanged<String?> onChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ModuleGate(
      module: 'BRANCHES',
      child: ref.watch(branchListProvider).when(
            loading: () => const SizedBox.shrink(),
            error: (_, __) => const SizedBox.shrink(),
            data: (rows) {
              if (rows.isEmpty) {
                return const SizedBox.shrink();
              }
              return Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                child: LocationPickerField(
                  branches: rows,
                  value: value,
                  allLabel: 'All locations',
                  onChanged: onChanged,
                ),
              );
            },
          ),
    );
  }
}

class LocationPickerField extends StatelessWidget {
  const LocationPickerField({
    super.key,
    required this.branches,
    required this.value,
    required this.onChanged,
    this.allLabel = 'All locations',
    this.allowAll = true,
  });

  final List<SiteBranch> branches;
  final String? value;
  final ValueChanged<String?> onChanged;
  final String allLabel;
  final bool allowAll;

  @override
  Widget build(BuildContext context) {
    var label = allLabel;
    if (value != null) {
      for (final b in branches) {
        if (b.id == value) {
          label = b.name;
          break;
        }
      }
    }
    return InkWell(
      borderRadius: BorderRadius.circular(16),
      onTap: () async {
        final picked = await showLocationPicker(
          context: context,
          branches: branches,
          selectedId: value,
          allLabel: allLabel,
          allowAll: allowAll,
        );
        if (picked == null) {
          return;
        }
        onChanged(picked.id);
      },
      child: InputDecorator(
        decoration: const InputDecoration(
          labelText: 'Location',
          prefixIcon: Icon(Icons.place_outlined),
          suffixIcon: Icon(Icons.expand_more),
        ),
        child: Text(label, overflow: TextOverflow.ellipsis),
      ),
    );
  }
}

class LocationChoice {
  const LocationChoice(this.id);
  final String? id;
}

Future<LocationChoice?> showLocationPicker({
  required BuildContext context,
  required List<SiteBranch> branches,
  required String? selectedId,
  String allLabel = 'All locations',
  bool allowAll = true,
}) {
  return showModalBottomSheet<LocationChoice>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    useRootNavigator: true,
    useSafeArea: true,
    builder: (ctx) {
      var query = '';
      return StatefulBuilder(
        builder: (ctx, setLocal) {
          final q = query.trim().toLowerCase();
          final filtered = q.isEmpty
              ? branches
              : branches
                  .where((b) =>
                      b.name.toLowerCase().contains(q) || (b.address ?? '').toLowerCase().contains(q))
                  .toList();
          return SafeArea(
            child: Padding(
              padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(ctx).bottom),
              child: SizedBox(
                height: MediaQuery.sizeOf(ctx).height * 0.62,
                child: Column(
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                      child: TextField(
                        autofocus: branches.length > 8,
                        decoration: const InputDecoration(
                          prefixIcon: Icon(Icons.search),
                          hintText: 'Search location',
                        ),
                        onChanged: (v) => setLocal(() => query = v),
                      ),
                    ),
                    Expanded(
                      child: ListView(
                        children: [
                          if (allowAll)
                            ListTile(
                              leading: Icon(selectedId == null ? Icons.check_circle : Icons.public_outlined),
                              title: Text(allLabel, style: const TextStyle(fontWeight: FontWeight.w700)),
                              selected: selectedId == null,
                              onTap: () => Navigator.pop(ctx, const LocationChoice(null)),
                            ),
                          for (final b in filtered)
                            ListTile(
                              leading: Icon(selectedId == b.id ? Icons.check_circle : Icons.place_outlined),
                              title: Text(b.name, style: const TextStyle(fontWeight: FontWeight.w700)),
                              subtitle: b.address == null ? null : Text(b.address!),
                              selected: selectedId == b.id,
                              onTap: () => Navigator.pop(ctx, LocationChoice(b.id)),
                            ),
                          if (filtered.isEmpty)
                            const Padding(
                              padding: EdgeInsets.all(24),
                              child: Text('No location matches that search.'),
                            ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      );
    },
  );
}

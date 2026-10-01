import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

final permissionCatalogueProvider = FutureProvider.autoDispose((ref) {
  return ref.watch(staffApiProvider).catalogue();
});

class AddStaffScreen extends ConsumerStatefulWidget {
  const AddStaffScreen({super.key});

  @override
  ConsumerState<AddStaffScreen> createState() => _AddStaffScreenState();
}

class _AddStaffScreenState extends ConsumerState<AddStaffScreen> {
  final _name = TextEditingController();
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _selected = <String>{};
  var _busy = false;
  String? _error;

  @override
  void dispose() {
    _name.dispose();
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(staffApiProvider).create(
            fullName: _name.text.trim(),
            email: _email.text.trim(),
            password: _password.text.trim().isEmpty ? null : _password.text,
            permissions: _selected.isEmpty ? null : _selected.toList(),
          );
      if (mounted) {
        context.pop();
      }
    } catch (e) {
      setState(() => _error = problemOf(e).detail);
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final catalogue = ref.watch(permissionCatalogueProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Add staff')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(controller: _name, decoration: const InputDecoration(labelText: 'Full name')),
          const SizedBox(height: 12),
          TextField(controller: _email, decoration: const InputDecoration(labelText: 'Email')),
          const SizedBox(height: 12),
          TextField(controller: _password, obscureText: true, decoration: const InputDecoration(labelText: 'Temporary password (optional if they already have DueMate)')),
          const SizedBox(height: 8),
          Text(
            'If this email already uses DueMate as an individual, they keep expenses and groups. They can switch between gym and My money with the same login.',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 20),
          Text('Permissions', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          catalogue.when(
            loading: () => const FsLoading(),
            error: (e, _) => Text(problemOf(e).detail),
            data: (items) => Wrap(
              spacing: 8,
              children: [
                for (final item in items)
                  FilterChip(
                    label: Text(item.code),
                    selected: _selected.contains(item.code),
                    onSelected: (on) => setState(() {
                      if (on) {
                        _selected.add(item.code);
                      } else {
                        _selected.remove(item.code);
                      }
                    }),
                  ),
              ],
            ),
          ),
          if (_error != null) ...[
            const SizedBox(height: 12),
            Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ],
          const SizedBox(height: 24),
          FilledButton(onPressed: _busy ? null : _save, child: const Text('Save')),
        ],
      ),
    );
  }
}

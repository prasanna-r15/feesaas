import 'package:feesaas_admin_web/slug.dart';
import 'package:feesaas_admin_web/tenant_logo_field.dart';
import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class CreateTenantPage extends ConsumerStatefulWidget {
  const CreateTenantPage({super.key});

  @override
  ConsumerState<CreateTenantPage> createState() => _CreateTenantPageState();
}

class _CreateTenantPageState extends ConsumerState<CreateTenantPage> {
  final _name = TextEditingController();
  final _slug = TextEditingController();
  final _ownerName = TextEditingController();
  final _ownerEmail = TextEditingController();
  final _ownerPassword = TextEditingController();
  var _type = 'GYM';
  var _busy = false;
  var _slugEdited = false;
  String? _error;
  String? _logo;

  @override
  void initState() {
    super.initState();
    _name.addListener(() {
      if (_slugEdited) {
        return;
      }
      _slug.text = slugify(_name.text);
    });
  }

  @override
  void dispose() {
    _name.dispose();
    _slug.dispose();
    _ownerName.dispose();
    _ownerEmail.dispose();
    _ownerPassword.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(platformApiProvider).createTenant(
            name: _name.text.trim(),
            slug: slugify(_slug.text.isEmpty ? _name.text : _slug.text),
            businessType: _type,
            ownerFullName: _ownerName.text.trim(),
            ownerEmail: _ownerEmail.text.trim(),
            ownerPassword: _ownerPassword.text,
            logoBase64: _logo,
          );
      if (mounted) context.go('/tenants');
    } catch (e) {
      setState(() => _error = problemOf(e).detail);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('New tenant')),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 520),
          child: ListView(
            padding: const EdgeInsets.all(24),
            children: [
              TextField(controller: _name, decoration: const InputDecoration(labelText: 'Business name')),
              const SizedBox(height: 12),
              TextField(
                controller: _slug,
                onChanged: (_) => _slugEdited = true,
                decoration: const InputDecoration(
                  labelText: 'URL slug',
                  helperText: 'Lowercase letters, numbers, and hyphens. Example: iron-man-unisex-gym',
                ),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField(
                value: _type,
                items: const [
                  DropdownMenuItem(value: 'GYM', child: Text('Gym')),
                  DropdownMenuItem(value: 'ACADEMY', child: Text('Academy')),
                  DropdownMenuItem(value: 'TUITION', child: Text('Tuition')),
                  DropdownMenuItem(value: 'GENERIC', child: Text('Generic')),
                ],
                onChanged: (v) => setState(() => _type = v ?? 'GYM'),
                decoration: const InputDecoration(labelText: 'Preset'),
              ),
              const SizedBox(height: 20),
              TenantLogoField(dataUri: _logo, onChanged: (v) => setState(() => _logo = v)),
              const SizedBox(height: 20),
              Text('Owner', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              TextField(controller: _ownerName, decoration: const InputDecoration(labelText: 'Full name')),
              const SizedBox(height: 12),
              TextField(controller: _ownerEmail, decoration: const InputDecoration(labelText: 'Email')),
              const SizedBox(height: 12),
              TextField(controller: _ownerPassword, obscureText: true, decoration: const InputDecoration(labelText: 'Password')),
              if (_error != null) ...[
                const SizedBox(height: 12),
                Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
              ],
              const SizedBox(height: 24),
              FilledButton(onPressed: _busy ? null : _save, child: const Text('Create tenant')),
            ],
          ),
        ),
      ),
    );
  }
}

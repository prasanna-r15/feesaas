import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class BusinessContactScreen extends ConsumerStatefulWidget {
  const BusinessContactScreen({super.key});

  @override
  ConsumerState<BusinessContactScreen> createState() => _BusinessContactScreenState();
}

class _BusinessContactScreenState extends ConsumerState<BusinessContactScreen> {
  final _phone = TextEditingController();
  final _whatsapp = TextEditingController();
  var _loading = true;
  var _busy = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final contact = await ref.read(settingsApiProvider).getContact();
      _phone.text = contact.phone ?? '';
      _whatsapp.text = contact.whatsappNumber ?? '';
    } catch (e) {
      _error = problemOf(e).detail;
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  void dispose() {
    _phone.dispose();
    _whatsapp.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(settingsApiProvider).saveContact(
            phone: _phone.text.trim(),
            whatsappNumber: _whatsapp.text.trim(),
          );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Saved. Reminders mention these numbers.')),
        );
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
    if (_loading) {
      return Scaffold(appBar: AppBar(title: const Text('Business numbers')), body: const FsLoading());
    }
    return Scaffold(
      appBar: AppBar(title: const Text('Business numbers')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text(
            'WhatsApp reminders open the app on this phone. To appear as your gym, stay logged into WhatsApp Business with the number below. SMS uses this phone’s SIM.',
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _whatsapp,
            keyboardType: TextInputType.phone,
            decoration: const InputDecoration(labelText: 'WhatsApp Business number'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _phone,
            keyboardType: TextInputType.phone,
            decoration: const InputDecoration(labelText: 'SMS / contact number'),
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

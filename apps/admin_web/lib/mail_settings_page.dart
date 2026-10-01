import 'package:feesaas_admin_web/admin_app_bar.dart';
import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final adminMailProvider = FutureProvider.autoDispose<DuesMailSettings>((ref) {
  return ref.watch(platformApiProvider).mailSettings();
});

class MailSettingsPage extends ConsumerStatefulWidget {
  const MailSettingsPage({super.key});

  @override
  ConsumerState<MailSettingsPage> createState() => _MailSettingsPageState();
}

class _MailSettingsPageState extends ConsumerState<MailSettingsPage> {
  final _host = TextEditingController();
  final _port = TextEditingController(text: '587');
  final _user = TextEditingController();
  final _password = TextEditingController();
  final _from = TextEditingController();
  var _loaded = false;
  var _enabled = false;
  var _cron = '0 0 8 * * *';
  var _busy = false;
  String? _error;
  String? _last;

  static const _schedules = [
    ('Daily 8:00 AM', '0 0 8 * * *'),
    ('Daily 9:00 AM', '0 0 9 * * *'),
    ('Daily 6:00 PM', '0 0 18 * * *'),
    ('Monday 8:00 AM', '0 0 8 * * MON'),
  ];

  @override
  void dispose() {
    _host.dispose();
    _port.dispose();
    _user.dispose();
    _password.dispose();
    _from.dispose();
    super.dispose();
  }

  void _hydrate(DuesMailSettings s) {
    if (_loaded) {
      return;
    }
    _loaded = true;
    _enabled = s.enabled;
    _cron = s.cronExpr;
    _host.text = s.smtpHost ?? '';
    _port.text = '${s.smtpPort}';
    _user.text = s.smtpUsername ?? '';
    _from.text = s.smtpFrom ?? '';
    _last = [
      if (s.lastRunAt != null && s.lastRunAt!.isNotEmpty) formatLocalDateTime(s.lastRunAt),
      if (s.lastResult != null && s.lastResult!.isNotEmpty) s.lastResult!,
    ].join('\n');
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(adminMailProvider);
    return AdminScaffold(
      title: 'Pending dues email',
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text(problemOf(e).detail)),
        data: (s) {
          _hydrate(s);
          return ListView(
            padding: const EdgeInsets.all(24),
            children: [
              const Text(
                'SMTP for owner dues digests and signup OTP. Edit email copy under Config. Member WhatsApp reminders are unchanged.',
              ),
              SwitchListTile(
                title: const Text('Enable automatic emails'),
                value: _enabled,
                onChanged: (v) => setState(() => _enabled = v),
              ),
              DropdownButtonFormField<String>(
                initialValue: _schedules.any((e) => e.$2 == _cron) ? _cron : _schedules.first.$2,
                decoration: const InputDecoration(labelText: 'Cron schedule (Asia/Kolkata)'),
                items: [for (final e in _schedules) DropdownMenuItem(value: e.$2, child: Text(e.$1))],
                onChanged: (v) => setState(() => _cron = v ?? _cron),
              ),
              TextField(controller: _host, decoration: const InputDecoration(labelText: 'SMTP host')),
              TextField(controller: _port, decoration: const InputDecoration(labelText: 'Port')),
              TextField(controller: _user, decoration: const InputDecoration(labelText: 'SMTP username')),
              TextField(
                controller: _password,
                obscureText: true,
                decoration: InputDecoration(labelText: s.smtpPasswordSet ? 'SMTP password (blank = keep)' : 'SMTP password'),
              ),
              TextField(controller: _from, decoration: const InputDecoration(labelText: 'From email')),
              if (_last != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(_last!)),
              if (_error != null) Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
              const SizedBox(height: 16),
              FilledButton(onPressed: _busy ? null : _save, child: const Text('Save')),
              const SizedBox(height: 8),
              OutlinedButton(onPressed: _busy ? null : _run, child: const Text('Send now')),
            ],
          );
        },
      ),
    );
  }

  Future<void> _save() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final current = ref.read(adminMailProvider).requireValue;
      await ref.read(platformApiProvider).saveMailSettings(
            DuesMailSettings(
              enabled: _enabled,
              cronExpr: _cron,
              timezone: 'Asia/Kolkata',
              smtpHost: _host.text.trim(),
              smtpPort: int.tryParse(_port.text.trim()) ?? 587,
              smtpUsername: _user.text.trim(),
              smtpPasswordSet: current.smtpPasswordSet,
              smtpFrom: _from.text.trim(),
            ),
            smtpPassword: _password.text.trim(),
          );
      _loaded = false;
      ref.invalidate(adminMailProvider);
    } catch (e) {
      setState(() => _error = problemOf(e).detail);
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _run() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result = await ref.read(platformApiProvider).runDuesEmail();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Emailed ${result.sent} · skipped ${result.skipped}')),
        );
      }
      _loaded = false;
      ref.invalidate(adminMailProvider);
    } catch (e) {
      setState(() => _error = problemOf(e).detail);
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }
}

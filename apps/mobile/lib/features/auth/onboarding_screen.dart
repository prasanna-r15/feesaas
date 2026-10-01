import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class OnboardingScreen extends ConsumerStatefulWidget {
  const OnboardingScreen({super.key});

  @override
  ConsumerState<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends ConsumerState<OnboardingScreen> {
  var _busy = false;
  var _join = false;
  String? _error;
  final _business = TextEditingController();
  final _city = TextEditingController();
  final _message = TextEditingController();

  @override
  void dispose() {
    _business.dispose();
    _city.dispose();
    _message.dispose();
    super.dispose();
  }

  Future<void> _finish({String location = '/personal'}) async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(sessionControllerProvider.notifier).completeOnboarding();
      if (mounted) context.go(location);
    } catch (e) {
      setState(() => _error = problemOf(e).detail);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _submitJoin() async {
    final name = _business.text.trim();
    if (name.isEmpty) {
      setState(() => _error = 'Enter the gym or business name.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result = await ref.read(authApiProvider).submitBusinessEnquiry(
            businessName: name,
            city: _city.text.trim(),
            message: _message.text.trim(),
          );
      await ref.read(sessionControllerProvider.notifier).completeOnboarding();
      if (!mounted) return;
      final note = result.emailed
          ? 'We emailed DueMate admin. Keep using your personal account — after they set up your business you can switch in as owner.'
          : 'We saved your request. Keep using your personal account while admin sets up your business.';
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(note)));
      context.go('/personal');
    } catch (e) {
      setState(() => _error = problemOf(e).detail);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Welcome')),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          Text(
            _join ? 'Open your business' : 'What do you want to do first?',
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 8),
          Text(
            _join
                ? 'Tell us the gym, yoga studio, or other centre you want to run. You keep this login. After admin approves, you become the owner.'
                : 'You can always switch later. Business owners and staff use the same login for fees and for their own money.',
            style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant),
          ),
          if (_error != null) ...[
            const SizedBox(height: 12),
            Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ],
          const SizedBox(height: 20),
          if (_join) ...[
            TextField(
              controller: _business,
              enabled: !_busy,
              textInputAction: TextInputAction.next,
              decoration: const InputDecoration(labelText: 'Gym or business name'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _city,
              enabled: !_busy,
              textInputAction: TextInputAction.next,
              decoration: const InputDecoration(labelText: 'City (optional)'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _message,
              enabled: !_busy,
              maxLines: 3,
              decoration: const InputDecoration(labelText: 'Anything we should know? (optional)'),
            ),
            const SizedBox(height: 20),
            FilledButton(
              onPressed: _busy ? null : _submitJoin,
              child: Text(_busy ? 'Sending…' : 'Send enquiry and continue'),
            ),
            const SizedBox(height: 8),
            TextButton(
              onPressed: _busy ? null : () => setState(() => _join = false),
              child: const Text('Back'),
            ),
          ] else ...[
            FilledButton.icon(
              onPressed: _busy ? null : () => _finish(),
              icon: const Icon(Icons.savings_outlined),
              label: const Text('Track my money'),
            ),
            const SizedBox(height: 10),
            OutlinedButton.icon(
              onPressed: _busy ? null : () => _finish(location: '/groups'),
              icon: const Icon(Icons.groups_outlined),
              label: const Text('Split with a group'),
            ),
            const SizedBox(height: 10),
            OutlinedButton.icon(
              onPressed: _busy
                  ? null
                  : () => setState(() {
                        _join = true;
                        _error = null;
                      }),
              icon: const Icon(Icons.storefront_outlined),
              label: const Text('Open my business'),
            ),
            const SizedBox(height: 16),
            TextButton(
              onPressed: _busy ? null : () => _finish(),
              child: const Text('Skip for now'),
            ),
          ],
        ],
      ),
    );
  }
}

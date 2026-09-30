import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _identifier = TextEditingController();
  final _password = TextEditingController();
  var _hidePassword = true;

  @override
  void dispose() {
    _identifier.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    await ref.read(sessionControllerProvider.notifier).login(_identifier.text, _password.text);
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(sessionControllerProvider);
    final busy = session.busy;
    return FsAuthScaffold(
      busy: busy,
      title: 'Welcome back',
      subtitle: 'Sign in to see who owes you money.',
      children: [
        TextField(
          controller: _identifier,
          enabled: !busy,
          keyboardType: TextInputType.emailAddress,
          textInputAction: TextInputAction.next,
          decoration: const InputDecoration(labelText: 'Email', prefixIcon: Icon(Icons.mail_outline)),
        ),
        const SizedBox(height: 14),
        TextField(
          controller: _password,
          enabled: !busy,
          obscureText: _hidePassword,
          onSubmitted: (_) {
            if (!busy) {
              _submit();
            }
          },
          decoration: InputDecoration(
            labelText: 'Password',
            prefixIcon: const Icon(Icons.lock_outline),
            suffixIcon: IconButton(
              onPressed: busy ? null : () => setState(() => _hidePassword = !_hidePassword),
              icon: Icon(_hidePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined),
            ),
          ),
        ),
        AnimatedSwitcher(
          duration: const Duration(milliseconds: 220),
          child: session.error == null
              ? const SizedBox.shrink()
              : Padding(
                  key: ValueKey(session.error),
                  padding: const EdgeInsets.only(top: 12),
                  child: Text(session.error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                ),
        ),
        const SizedBox(height: 22),
        FilledButton(
          onPressed: busy ? null : _submit,
          child: const Text('Sign in'),
        ),
        const SizedBox(height: 16),
        TextButton(
          onPressed: busy
              ? null
              : () {
                  final next = GoRouterState.of(context).uri.queryParameters['next'];
                  context.go(next == null || next.isEmpty ? '/signup' : '/signup?next=${Uri.encodeComponent(next)}');
                },
          child: const Text('Create personal account'),
        ),
      ],
    );
  }
}

import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class SignupScreen extends ConsumerStatefulWidget {
  const SignupScreen({super.key});

  @override
  ConsumerState<SignupScreen> createState() => _SignupScreenState();
}

class _SignupScreenState extends ConsumerState<SignupScreen> {
  final _name = TextEditingController();
  final _email = TextEditingController();
  final _phone = TextEditingController();
  final _password = TextEditingController();
  final _otp = TextEditingController();
  RegisterChallenge? _challenge;

  @override
  void dispose() {
    _name.dispose();
    _email.dispose();
    _phone.dispose();
    _password.dispose();
    _otp.dispose();
    super.dispose();
  }

  Future<void> _sendCode() async {
    final email = _email.text.trim();
    final phone = _phone.text.trim();
    if (email.isEmpty || phone.isEmpty) {
      return;
    }
    final challenge = await ref.read(sessionControllerProvider.notifier).startRegister(
          fullName: _name.text.trim(),
          email: email,
          phone: phone,
          password: _password.text,
        );
    if (challenge != null && mounted) {
      setState(() => _challenge = challenge);
    }
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(sessionControllerProvider);
    final busy = session.busy;
    final challenge = _challenge;
    return FsAuthScaffold(
      busy: busy,
      busyLabel: challenge == null ? 'Sending code…' : 'Verifying…',
      title: challenge == null ? 'Create account' : 'Check your email',
      subtitle: challenge == null
          ? 'Email and phone are required. We send a one-time code to your email.'
          : 'Enter the 6-digit code sent to ${challenge.destination}.',
      children: [
        if (challenge == null) ...[
          TextField(
            controller: _name,
            enabled: !busy,
            textCapitalization: TextCapitalization.words,
            decoration: const InputDecoration(labelText: 'Your name', prefixIcon: Icon(Icons.badge_outlined)),
          ),
          const SizedBox(height: 14),
          TextField(
            controller: _email,
            enabled: !busy,
            keyboardType: TextInputType.emailAddress,
            decoration: const InputDecoration(labelText: 'Email', prefixIcon: Icon(Icons.mail_outline)),
          ),
          const SizedBox(height: 14),
          TextField(
            controller: _phone,
            enabled: !busy,
            keyboardType: TextInputType.phone,
            decoration: const InputDecoration(labelText: 'Phone', prefixIcon: Icon(Icons.phone_outlined)),
          ),
          const SizedBox(height: 14),
          TextField(
            controller: _password,
            enabled: !busy,
            obscureText: true,
            decoration: const InputDecoration(labelText: 'Password', prefixIcon: Icon(Icons.lock_outline)),
          ),
        ] else ...[
          TextField(
            controller: _otp,
            enabled: !busy,
            keyboardType: TextInputType.number,
            textInputAction: TextInputAction.done,
            maxLength: 6,
            decoration: const InputDecoration(labelText: '6-digit code', prefixIcon: Icon(Icons.pin_outlined), counterText: ''),
          ),
        ],
        if (session.error != null) ...[
          const SizedBox(height: 12),
          Text(session.error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
        ],
        const SizedBox(height: 22),
        FilledButton(
          onPressed: busy
              ? null
              : challenge == null
                  ? _sendCode
                  : () => ref.read(sessionControllerProvider.notifier).verifyRegister(challenge.challengeId, _otp.text),
          child: Text(challenge == null ? 'Send verification code' : 'Verify and create account'),
        ),
        if (challenge != null)
          TextButton(
            onPressed: busy
                ? null
                : () {
                    setState(() {
                      _challenge = null;
                      _otp.clear();
                    });
                  },
            child: const Text('Use a different email'),
          ),
        TextButton(
          onPressed: busy
              ? null
              : () {
                  final next = GoRouterState.of(context).uri.queryParameters['next'];
                  context.go(next == null || next.isEmpty ? '/login' : '/login?next=${Uri.encodeComponent(next)}');
                },
          child: const Text('Already have an account? Sign in'),
        ),
      ],
    );
  }
}

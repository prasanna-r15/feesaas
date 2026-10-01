import 'package:feesaas_core/src/network/api_providers.dart';
import 'package:feesaas_core/src/network/auth_interceptor.dart';
import 'package:feesaas_core/src/ui/widgets.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class ForgotPasswordScreen extends ConsumerStatefulWidget {
  const ForgotPasswordScreen({super.key});

  @override
  ConsumerState<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends ConsumerState<ForgotPasswordScreen> {
  final _email = TextEditingController();
  final _otp = TextEditingController();
  final _password = TextEditingController();
  String? _challengeId;
  var _step = 0;
  var _busy = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _otp.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    final email = _email.text.trim();
    if (email.isEmpty) {
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result = await ref.read(authApiProvider).forgotPassword(email);
      if (!mounted) {
        return;
      }
      if (result.challengeId == null || result.challengeId!.isEmpty) {
        setState(() {
          _error = 'If that email is registered, we sent a code. Check inbox, or try again.';
        });
        return;
      }
      setState(() {
        _challengeId = result.challengeId;
        _step = 1;
      });
    } catch (e) {
      setState(() => _error = problemOf(e).detail);
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _save() async {
    final challengeId = _challengeId;
    final otp = _otp.text.trim();
    final password = _password.text;
    if (challengeId == null || otp.length != 6 || password.length < 8) {
      setState(() => _error = 'Enter the 6-digit code and a new password (8+ characters).');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(authApiProvider).resetPassword(
            challengeId: challengeId,
            otp: otp,
            newPassword: password,
          );
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Password updated. Sign in with the new one.')),
      );
      context.go('/login');
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
    return FsAuthScaffold(
      busy: _busy,
      busyLabel: _step == 0 ? 'Sending code…' : 'Saving…',
      title: _step == 0 ? 'Forgot password' : 'Set a new password',
      subtitle: _step == 0
          ? 'We email a 6-digit code to reset your password.'
          : 'Enter the code sent to ${_email.text.trim()} and choose a new password.',
      children: [
        if (_step == 0)
          TextField(
            controller: _email,
            enabled: !_busy,
            keyboardType: TextInputType.emailAddress,
            decoration: const InputDecoration(labelText: 'Email', prefixIcon: Icon(Icons.mail_outline)),
          )
        else ...[
          TextField(
            controller: _otp,
            enabled: !_busy,
            keyboardType: TextInputType.number,
            maxLength: 6,
            decoration: const InputDecoration(
              labelText: '6-digit code',
              prefixIcon: Icon(Icons.pin_outlined),
              counterText: '',
            ),
          ),
          const SizedBox(height: 14),
          TextField(
            controller: _password,
            enabled: !_busy,
            obscureText: true,
            decoration: const InputDecoration(
              labelText: 'New password',
              prefixIcon: Icon(Icons.lock_outline),
            ),
          ),
        ],
        if (_error != null) ...[
          const SizedBox(height: 12),
          Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
        ],
        const SizedBox(height: 22),
        FilledButton(
          onPressed: _busy ? null : (_step == 0 ? _send : _save),
          child: Text(_step == 0 ? 'Send code' : 'Update password'),
        ),
        TextButton(
          onPressed: _busy
              ? null
              : () {
                  if (_step == 1) {
                    setState(() {
                      _step = 0;
                      _otp.clear();
                      _password.clear();
                    });
                  } else {
                    context.go('/login');
                  }
                },
          child: Text(_step == 1 ? 'Use a different email' : 'Back to sign in'),
        ),
      ],
    );
  }
}

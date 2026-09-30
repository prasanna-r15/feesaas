import 'package:feesaas_api_client/feesaas_api_client.dart';
import 'package:feesaas_core/src/auth/session_hooks.dart';
import 'package:feesaas_core/src/auth/token_store.dart';
import 'package:feesaas_core/src/config/tenant_config.dart';
import 'package:feesaas_core/src/network/api_providers.dart';
import 'package:feesaas_core/src/network/auth_interceptor.dart';
import 'package:feesaas_core/src/network/dio_factory.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

enum AuthStatus { unknown, signedOut, signedIn, suspended }

class SessionState {
  const SessionState({required this.status, this.error, this.busy = false});

  final AuthStatus status;
  final String? error;
  final bool busy;

  static const unknown = SessionState(status: AuthStatus.unknown);
  static const signedOut = SessionState(status: AuthStatus.signedOut);
  static const signedIn = SessionState(status: AuthStatus.signedIn);
  static const suspended = SessionState(status: AuthStatus.suspended);
}

class SessionController extends StateNotifier<SessionState> {
  SessionController(this._ref) : super(SessionState.unknown) {
    _ref.read(sessionHooksProvider).onInvalid = _localSignOut;
    restore();
  }

  final Ref _ref;
  TokenStore get _store => _ref.read(tokenStoreProvider);
  AuthApi get _api => _ref.read(authApiProvider);

  Future<void> restore() async {
    try {
      final access = await _store.readAccess();
      final refresh = await _store.readRefresh();
      if (access == null && refresh == null) {
        state = SessionState.signedOut;
        return;
      }
      await _loadBootstrap();
    } catch (_) {
      state = SessionState.signedOut;
    }
  }

  Future<void> login(String identifier, String password) async {
    if (state.busy) {
      return;
    }
    state = const SessionState(status: AuthStatus.signedOut, busy: true);
    try {
      final tokens = await _api.login(
        identifier: identifier.trim(),
        password: password,
        deviceId: await _store.deviceId(),
      );
      await _store.saveTokens(access: tokens.accessToken, refresh: tokens.refreshToken);
      await _loadBootstrap();
    } catch (e) {
      final problem = problemOf(e);
      if (problem.code == 'TENANT_SUSPENDED') {
        state = SessionState(status: AuthStatus.suspended, error: problem.detail);
        return;
      }
      state = SessionState(status: AuthStatus.signedOut, error: problem.detail);
    }
  }

  Future<RegisterChallenge?> startRegister({
    required String fullName,
    required String email,
    required String phone,
    required String password,
  }) async {
    if (state.busy) {
      return null;
    }
    state = const SessionState(status: AuthStatus.signedOut, busy: true);
    try {
      final challenge = await _api.startRegister(
        fullName: fullName,
        email: email,
        phone: phone,
        password: password,
      );
      state = SessionState.signedOut;
      return challenge;
    } catch (e) {
      final problem = problemOf(e);
      state = SessionState(status: AuthStatus.signedOut, error: problem.detail);
      return null;
    }
  }

  Future<void> verifyRegister(String challengeId, String otp) async {
    if (state.busy) {
      return;
    }
    state = const SessionState(status: AuthStatus.signedOut, busy: true);
    try {
      final tokens = await _api.verifyRegister(
        challengeId: challengeId,
        otp: otp.trim(),
        deviceId: await _store.deviceId(),
      );
      await _store.saveTokens(access: tokens.accessToken, refresh: tokens.refreshToken);
      await _loadBootstrap();
    } catch (e) {
      final problem = problemOf(e);
      state = SessionState(status: AuthStatus.signedOut, error: problem.detail);
    }
  }

  Future<void> switchContext(UserContext context) async {
    final tokens = await _api.switchContext(
      kind: context.kind,
      tenantId: context.tenantId,
      workspaceId: context.workspaceId,
      groupId: context.groupId,
      deviceId: await _store.deviceId(),
    );
    await _store.saveTokens(access: tokens.accessToken, refresh: tokens.refreshToken);
    await _loadBootstrap();
  }

  Future<void> completeOnboarding() async {
    await _api.completeOnboarding();
    await _loadBootstrap();
  }

  Future<void> logout() async {
    final refresh = await _store.readRefresh();
    if (refresh != null) {
      try {
        await _api.logout(refresh);
      } catch (_) {}
    }
    await _store.clearTokens();
    _ref.read(tenantConfigProvider.notifier).state = null;
    state = SessionState.signedOut;
  }

  void _localSignOut() {
    _ref.read(tenantConfigProvider.notifier).state = null;
    state = SessionState.signedOut;
  }

  Future<void> _loadBootstrap() async {
    try {
      final bootstrap = await _api.bootstrap();
      final config = TenantConfig(bootstrap);
      _ref.read(tenantConfigProvider.notifier).state = config;
      state = config.isSuspended ? SessionState.suspended : SessionState.signedIn;
    } catch (e) {
      final problem = problemOf(e);
      if (problem.code == 'TENANT_SUSPENDED' || problem.status == 403) {
        state = SessionState.suspended;
        return;
      }
      await _store.clearTokens();
      _ref.read(tenantConfigProvider.notifier).state = null;
      state = SessionState.signedOut;
    }
  }
}

final sessionControllerProvider =
    StateNotifierProvider<SessionController, SessionState>((ref) => SessionController(ref));

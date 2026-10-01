import 'dart:async';

import 'package:feesaas_core/src/auth/session_controller.dart';
import 'package:feesaas_core/src/network/api_providers.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final mySupportUnreadProvider =
    NotifierProvider<MySupportUnreadNotifier, int>(MySupportUnreadNotifier.new);

final platformSupportUnreadProvider =
    NotifierProvider<PlatformSupportUnreadNotifier, int>(PlatformSupportUnreadNotifier.new);

class MySupportUnreadNotifier extends Notifier<int> {
  Timer? _timer;
  int _epoch = 0;
  int _lastStarted = 0;
  int _lastApplied = 0;

  @override
  int build() {
    ref.onDispose(() {
      _timer?.cancel();
      _epoch++;
    });
    final signedIn = ref.watch(sessionControllerProvider.select((s) => s.status == AuthStatus.signedIn));
    _timer?.cancel();
    if (!signedIn) {
      _epoch++;
      return 0;
    }
    _timer = Timer.periodic(const Duration(seconds: 2), (_) => refresh());
    Future.microtask(refresh);
    return 0;
  }

  Future<void> refresh() async {
    if (!_signedIn) {
      return;
    }
    final epoch = _epoch;
    final started = ++_lastStarted;
    try {
      final count = await ref.read(authApiProvider).mySupportUnread();
      if (epoch != _epoch || !_signedIn || started < _lastApplied) {
        return;
      }
      _lastApplied = started;
      state = count;
    } catch (_) {}
  }

  bool get _signedIn => ref.read(sessionControllerProvider).status == AuthStatus.signedIn;
}

class PlatformSupportUnreadNotifier extends Notifier<int> {
  Timer? _timer;
  int _epoch = 0;
  int _lastStarted = 0;
  int _lastApplied = 0;

  @override
  int build() {
    ref.onDispose(() {
      _timer?.cancel();
      _epoch++;
    });
    final signedIn = ref.watch(sessionControllerProvider.select((s) => s.status == AuthStatus.signedIn));
    _timer?.cancel();
    if (!signedIn) {
      _epoch++;
      return 0;
    }
    _timer = Timer.periodic(const Duration(seconds: 2), (_) => refresh());
    Future.microtask(refresh);
    return 0;
  }

  Future<void> refresh() async {
    if (!_signedIn) {
      return;
    }
    final epoch = _epoch;
    final started = ++_lastStarted;
    try {
      final count = await ref.read(platformApiProvider).supportUnread();
      if (epoch != _epoch || !_signedIn || started < _lastApplied) {
        return;
      }
      _lastApplied = started;
      state = count;
    } catch (_) {}
  }

  bool get _signedIn => ref.read(sessionControllerProvider).status == AuthStatus.signedIn;
}

import 'package:feesaas_admin_web/mail_settings_page.dart';
import 'package:feesaas_admin_web/config_page.dart';
import 'package:feesaas_admin_web/create_tenant_page.dart';
import 'package:feesaas_admin_web/edit_tenant_page.dart';
import 'package:feesaas_admin_web/login_page.dart';
import 'package:feesaas_admin_web/tenant_members_page.dart';
import 'package:feesaas_admin_web/tenants_page.dart';
import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

final adminRouterProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/tenants',
    refreshListenable: _Refresh(ref),
    redirect: (context, state) {
      final session = ref.read(sessionControllerProvider);
      final loggingIn = state.matchedLocation == '/login';
      return switch (session.status) {
        AuthStatus.unknown => loggingIn ? null : '/login',
        AuthStatus.signedOut => loggingIn ? null : '/login',
        AuthStatus.suspended => '/login',
        AuthStatus.signedIn => loggingIn ? '/tenants' : null,
      };
    },
    routes: [
      GoRoute(path: '/login', builder: (c, s) => const AdminLoginPage()),
      GoRoute(path: '/tenants', builder: (c, s) => const TenantsPage()),
      GoRoute(path: '/mail', builder: (c, s) => const MailSettingsPage()),
      GoRoute(path: '/config', builder: (c, s) => const ConfigPage()),
      GoRoute(path: '/tenants/new', builder: (c, s) => const CreateTenantPage()),
      GoRoute(
        path: '/tenants/:id',
        builder: (c, s) => EditTenantPage(tenantId: s.pathParameters['id']!),
        routes: [
          GoRoute(
            path: 'members',
            builder: (c, s) => TenantMembersPage(tenantId: s.pathParameters['id']!),
          ),
        ],
      ),
    ],
  );
});

class _Refresh extends ChangeNotifier {
  _Refresh(this._ref) {
    _ref.listen<SessionState>(sessionControllerProvider, (_, __) => notifyListeners());
  }

  final Ref _ref;
}

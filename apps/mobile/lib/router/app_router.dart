import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/features/auth/login_screen.dart';
import 'package:feesaas_mobile/features/auth/onboarding_screen.dart';
import 'package:feesaas_mobile/features/auth/signup_screen.dart';
import 'package:feesaas_mobile/features/auth/suspended_screen.dart';
import 'package:feesaas_mobile/features/catalog/catalog_screens.dart';
import 'package:feesaas_mobile/features/customers/customer_form_screen.dart';
import 'package:feesaas_mobile/features/customers/customer_list_screen.dart';
import 'package:feesaas_mobile/features/fee_plans/fee_plan_form_screen.dart';
import 'package:feesaas_mobile/features/fee_plans/fee_plan_list_screen.dart';
import 'package:feesaas_mobile/features/attendance/batches_screen.dart';
import 'package:feesaas_mobile/features/groups/group_detail_screen.dart';
import 'package:feesaas_mobile/features/groups/group_invite_screen.dart';
import 'package:feesaas_mobile/features/groups/group_list_screen.dart';
import 'package:feesaas_mobile/features/import/import_members_screen.dart';
import 'package:feesaas_mobile/features/more/more_screen.dart';
import 'package:feesaas_mobile/features/payments/payments_screen.dart';
import 'package:feesaas_mobile/features/personal/personal_budgets_screen.dart';
import 'package:feesaas_mobile/features/personal/personal_dashboard_screen.dart';
import 'package:feesaas_mobile/features/personal/personal_expenses_screen.dart';
import 'package:feesaas_mobile/features/personal/business_enquiry_sheet.dart';
import 'package:feesaas_mobile/features/personal/personal_profile_screen.dart';
import 'package:feesaas_mobile/features/personal/personal_shell.dart';
import 'package:feesaas_mobile/features/personal/support_chat_screen.dart';
import 'package:feesaas_mobile/features/platform/dues_mail_settings_screen.dart';
import 'package:feesaas_mobile/features/platform/gyms_screen.dart';
import 'package:feesaas_mobile/features/pending_fees/pending_fees_screen.dart';
import 'package:feesaas_mobile/features/reports/reports_screen.dart';
import 'package:feesaas_mobile/features/settings/business_contact_screen.dart';
import 'package:feesaas_mobile/features/staff/add_staff_screen.dart';
import 'package:feesaas_mobile/features/staff/staff_list_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

final appRouterProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/pending',
    refreshListenable: _SessionRefresh(ref),
    redirect: (context, state) {
      final session = ref.read(sessionControllerProvider);
      final loc = state.matchedLocation;
      final loggingIn = loc == '/login' || loc == '/signup' || loc == '/forgot';
      final next = state.uri.queryParameters['next'];
      final inviteNext = (next != null && next.startsWith('/group/invite/')) ? next : null;
      return switch (session.status) {
        AuthStatus.unknown => loggingIn ? null : '/login',
        AuthStatus.signedOut => loggingIn || loc.startsWith('/group/invite') ? null : '/login',
        AuthStatus.suspended => loc == '/suspended' ? null : '/suspended',
        AuthStatus.signedIn => () {
            final config = ref.read(tenantConfigProvider);
            if (loggingIn || loc == '/suspended') {
              return inviteNext ?? config?.homeLocation ?? '/pending';
            }
            if (config == null) {
              return null;
            }
            if (config.needsOnboarding && loc != '/onboarding') {
              return '/onboarding';
            }
            if (config.isPersonal && loc != '/onboarding' && !loc.startsWith('/personal') && !loc.startsWith('/groups') && !loc.startsWith('/group/invite')) {
              return '/personal';
            }
            if (!config.isPersonal && !config.isPlatform && (loc.startsWith('/personal') || loc.startsWith('/groups'))) {
              return '/pending';
            }
            return null;
          }(),
      };
    },
    routes: [
      GoRoute(path: '/login', builder: (c, s) => const LoginScreen()),
      GoRoute(path: '/signup', builder: (c, s) => const SignupScreen()),
      GoRoute(path: '/forgot', builder: (c, s) => const ForgotPasswordScreen()),
      GoRoute(path: '/onboarding', builder: (c, s) => const OnboardingScreen()),
      GoRoute(path: '/suspended', builder: (c, s) => const SuspendedScreen()),
      GoRoute(path: '/group/invite/:token', builder: (c, s) => GroupInviteScreen(token: s.pathParameters['token']!)),
      GoRoute(path: '/staff/new', builder: (c, s) => const AddStaffScreen()),
      GoRoute(path: '/fee-plans/new', builder: (c, s) => const FeePlanFormScreen()),
      GoRoute(
        path: '/fee-plans/:id',
        builder: (c, s) => FeePlanFormScreen(planId: s.pathParameters['id']),
      ),
      GoRoute(path: '/customers/new', builder: (c, s) => const CustomerFormScreen()),
      GoRoute(
        path: '/customers/:id',
        builder: (c, s) => CustomerFormScreen(customerId: s.pathParameters['id']),
      ),
      StatefulShellRoute.indexedStack(
        builder: (context, state, shell) => PersonalShell(navigationShell: shell),
        branches: [
          StatefulShellBranch(routes: [GoRoute(path: '/personal', builder: (c, s) => const PersonalDashboardScreen())]),
          StatefulShellBranch(routes: [GoRoute(path: '/personal/expenses', builder: (c, s) => const PersonalExpensesScreen())]),
          StatefulShellBranch(routes: [GoRoute(path: '/personal/budgets', builder: (c, s) => const PersonalBudgetsScreen())]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/groups',
              builder: (c, s) => const GroupListScreen(),
              routes: [
                GoRoute(
                  path: ':id',
                  builder: (c, s) => GroupDetailScreen(groupId: s.pathParameters['id']!),
                ),
              ],
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/personal/profile',
              builder: (c, s) => const PersonalProfileScreen(),
              routes: [
                GoRoute(path: 'enquiry', builder: (c, s) => const BusinessEnquiryScreen()),
                GoRoute(path: 'support', builder: (c, s) => const SupportChatScreen()),
              ],
            ),
          ]),
        ],
      ),
      StatefulShellRoute.indexedStack(
        builder: (context, state, shell) => AppShell(navigationShell: shell),
        branches: [
          StatefulShellBranch(routes: [
            GoRoute(path: '/pending', builder: (c, s) => const PendingFeesScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/customers', builder: (c, s) => const CustomerListScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/reports', builder: (c, s) => const ReportsScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/more',
              builder: (c, s) => const MoreScreen(),
              routes: [
                GoRoute(path: 'staff', builder: (c, s) => const StaffListScreen()),
                GoRoute(path: 'fee-plans', builder: (c, s) => const FeePlanListScreen()),
                GoRoute(path: 'contact', builder: (c, s) => const BusinessContactScreen()),
                GoRoute(path: 'payments', builder: (c, s) => const PaymentsScreen()),
                GoRoute(path: 'import', builder: (c, s) => const ImportMembersScreen()),
                GoRoute(path: 'batches', builder: (c, s) => const BatchesScreen()),
                GoRoute(
                  path: 'batches/:id',
                  builder: (c, s) => AttendanceScreen(batchId: s.pathParameters['id']!),
                ),
                GoRoute(path: 'branches', builder: (c, s) => const BranchesScreen()),
                GoRoute(path: 'addons', builder: (c, s) => const AddonsScreen()),
                GoRoute(path: 'diet-charts', builder: (c, s) => const DietChartsScreen()),
                GoRoute(path: 'gyms', builder: (c, s) => const GymsScreen()),
                GoRoute(path: 'mail', builder: (c, s) => const DuesMailSettingsScreen()),
                GoRoute(path: 'support', builder: (c, s) => const SupportChatScreen()),
              ],
            ),
          ]),
        ],
      ),
    ],
  );
});

class _SessionRefresh extends ChangeNotifier {
  _SessionRefresh(this._ref) {
    _ref.listen<SessionState>(sessionControllerProvider, (_, __) => notifyListeners());
    _ref.listen(tenantConfigProvider, (_, __) => notifyListeners());
  }

  final Ref _ref;
}

class AppShell extends ConsumerWidget {
  const AppShell({super.key, required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(tenantConfigProvider);
    final unread = ref.watch(mySupportUnreadProvider);
    final customersLabel = config?.label('customer.plural', 'Customers') ?? 'Customers';
    final destinations = <_Dest>[
      const _Dest(label: 'Pending', icon: Icons.payments_outlined, module: null, permission: 'fees.view'),
      _Dest(label: customersLabel, icon: Icons.people_outline, module: 'CUSTOMERS', permission: 'customers.view'),
      const _Dest(label: 'Reports', icon: Icons.bar_chart_outlined, module: 'REPORTS', permission: 'reports.view'),
      const _Dest(label: 'More', icon: Icons.more_horiz, module: null, permission: null),
    ];
    final visible = <int>[];
    for (var i = 0; i < destinations.length; i++) {
      final d = destinations[i];
      final moduleOk = d.module == null || config == null || config.hasModule(d.module!);
      final permOk = d.permission == null || config == null || config.hasPermission(d.permission!);
      if (moduleOk && permOk) {
        visible.add(i);
      }
    }
    if (!visible.contains(0)) {
      visible.insert(0, 0);
    }
    if (!visible.contains(3)) {
      visible.add(3);
    }
    final currentBranch = navigationShell.currentIndex;
    var selected = visible.indexOf(currentBranch);
    if (selected < 0) {
      selected = 0;
    }
    final barDestinations = [
      for (final i in visible)
        NavigationDestination(
          icon: i == 3
              ? FsUnreadBadge(count: unread, child: Icon(destinations[i].icon))
              : Icon(destinations[i].icon),
          label: destinations[i].label,
        ),
    ];
    return Scaffold(
      body: navigationShell,
      bottomNavigationBar: barDestinations.length < 2
          ? null
          : NavigationBar(
              selectedIndex: selected.clamp(0, barDestinations.length - 1),
              destinations: barDestinations,
              onDestinationSelected: (i) {
                tickWorkspace(ref);
                navigationShell.goBranch(visible[i]);
              },
            ),
    );
  }
}

class _Dest {
  const _Dest({required this.label, required this.icon, required this.module, required this.permission});

  final String label;
  final IconData icon;
  final String? module;
  final String? permission;
}

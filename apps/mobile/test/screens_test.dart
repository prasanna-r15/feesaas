import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/app.dart';
import 'package:feesaas_mobile/features/auth/login_screen.dart';
import 'package:feesaas_mobile/features/customers/customer_form_screen.dart';
import 'package:feesaas_mobile/features/customers/customer_list_screen.dart';
import 'package:feesaas_mobile/features/fee_plans/fee_plan_form_screen.dart';
import 'package:feesaas_mobile/features/fee_plans/fee_plan_list_screen.dart';
import 'package:feesaas_mobile/features/auth/suspended_screen.dart';
import 'package:feesaas_mobile/features/more/more_screen.dart';
import 'package:feesaas_mobile/features/pending_fees/pending_fees_screen.dart';
import 'package:feesaas_mobile/features/payments/payments_screen.dart';
import 'package:feesaas_mobile/features/reports/reports_screen.dart';
import 'package:feesaas_mobile/features/staff/add_staff_screen.dart';
import 'package:feesaas_mobile/features/staff/staff_list_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('login screen shows DueMate branding', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: FeeSaasApp()));
    await tester.pump();
    expect(find.byType(DueMateLogo), findsWidgets);
    expect(find.text('Sign in to see who owes you money.'), findsOneWidget);
    expect(find.text('Sign in'), findsOneWidget);
  });

  testWidgets('pending fees screen renders', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          pendingFeesProvider.overrideWith((ref) async => <PendingFee>[]),
          pendingSummaryProvider.overrideWith((ref) async => PendingSummary.empty()),
        ],
        child: const MaterialApp(home: PendingFeesScreen()),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Pending fees'), findsOneWidget);
    expect(find.text('No pending fees yet'), findsOneWidget);
  });

  testWidgets('pending fees list shows amount and due date', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          pendingSummaryProvider.overrideWith((ref) async => PendingSummary.empty()),
          pendingFeesProvider.overrideWith((ref) async => [
                PendingFee(
                  id: 'f1',
                  customerId: 'c1',
                  customerName: 'Rahul Sharma',
                  customerCode: 'C0001',
                  dueDate: '2026-10-15',
                  grossMinor: 150000,
                  paidMinor: 0,
                  outstandingMinor: 150000,
                  currency: 'INR',
                  outstandingLabel: '₹1,500.00',
                  status: 'PENDING',
                  effectiveStatus: 'PENDING',
                  planName: 'Monthly fee',
                ),
              ]),
        ],
        child: const MaterialApp(home: PendingFeesScreen()),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Rahul Sharma'), findsOneWidget);
    expect(find.text('₹1,500.00'), findsOneWidget);
    expect(find.textContaining('Due 2026-10-15'), findsOneWidget);
  });

  testWidgets('members list shows due date', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          customerListProvider.overrideWith((ref) async => [
                Customer(
                  id: 'c1',
                  customerCode: 'C0001',
                  fullName: 'Rahul Sharma',
                  phone: '+919876543210',
                  status: 'ACTIVE',
                  dueDate: '2026-10-15',
                ),
              ]),
        ],
        child: const MaterialApp(home: CustomerListScreen()),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Rahul Sharma'), findsOneWidget);
    expect(find.textContaining('Due 2026-10-15'), findsOneWidget);
    expect(find.text('ACTIVE'), findsOneWidget);
  });

  testWidgets('members list shows overdue chip', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          customerListProvider.overrideWith((ref) async => [
                Customer(
                  id: 'c2',
                  customerCode: 'C0002',
                  fullName: 'Late Payer',
                  status: 'ACTIVE',
                  dueDate: '2020-01-01',
                ),
              ]),
        ],
        child: const MaterialApp(home: CustomerListScreen()),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Late Payer'), findsOneWidget);
    expect(find.text('Overdue'), findsOneWidget);
  });

  testWidgets('add member form includes due date', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          feePlanListProvider.overrideWith((ref) async => [_samplePlan()]),
        ],
        child: const MaterialApp(home: CustomerFormScreen()),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Due date'), findsOneWidget);
    expect(find.text('Full name'), findsOneWidget);
    expect(find.text('Has WhatsApp'), findsOneWidget);
    expect(find.text('Fee plan'), findsOneWidget);
    expect(find.byType(FilledButton, skipOffstage: false), findsOneWidget);
  });

  testWidgets('fee plan list shows names and amounts', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          feePlanListProvider.overrideWith((ref) async => [_samplePlan()]),
        ],
        child: const MaterialApp(home: FeePlanListScreen()),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('General'), findsOneWidget);
    expect(find.textContaining('₹1,500.00'), findsOneWidget);
  });

  testWidgets('add fee plan form renders', (tester) async {
    await tester.pumpWidget(
      const ProviderScope(child: MaterialApp(home: FeePlanFormScreen())),
    );
    expect(find.text('Add plan'), findsOneWidget);
    expect(find.text('Amount (₹)'), findsOneWidget);
  });

  testWidgets('reports screen shows collection totals', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          reportOverviewProvider.overrideWith((ref) async => ReportOverview.empty()),
        ],
        child: const MaterialApp(home: ReportsScreen()),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Reports'), findsOneWidget);
    expect(find.text('Collected this month'), findsOneWidget);
    expect(find.text('Outstanding by plan'), findsOneWidget);
    expect(find.byTooltip('Export Excel'), findsOneWidget);
  });

  testWidgets('receipts screen empty state renders', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          paymentsProvider.overrideWith((ref) async => <PaymentRecord>[]),
        ],
        child: const MaterialApp(home: PaymentsScreen()),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Receipts'), findsOneWidget);
    expect(find.text('No receipts yet'), findsOneWidget);
  });

  testWidgets('more screen renders', (tester) async {
    await tester.pumpWidget(
      const ProviderScope(
        child: MaterialApp(home: MoreScreen()),
      ),
    );
    expect(find.text('More'), findsOneWidget);
    expect(find.text('Sign out'), findsOneWidget);
  });

  testWidgets('staff list empty state renders', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          staffListProvider.overrideWith((ref) async => <StaffMember>[]),
        ],
        child: const MaterialApp(home: StaffListScreen()),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Staff'), findsOneWidget);
    expect(find.text('No staff yet'), findsOneWidget);
  });

  testWidgets('add staff form renders', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          permissionCatalogueProvider.overrideWith((ref) async => <PermissionDef>[]),
        ],
        child: const MaterialApp(home: AddStaffScreen()),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Add staff'), findsOneWidget);
    expect(find.text('Permissions'), findsOneWidget);
    expect(find.text('Save'), findsOneWidget);
  });

  testWidgets('suspended screen renders', (tester) async {
    await tester.pumpWidget(
      const ProviderScope(child: MaterialApp(home: SuspendedScreen())),
    );
    expect(find.text('This business is suspended'), findsOneWidget);
  });

  testWidgets('login fields accept input', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: MaterialApp(home: LoginScreen())));
    await tester.enterText(find.byType(TextField).first, 'owner@demo.local');
    await tester.enterText(find.byType(TextField).last, 'welcome123');
    expect(find.text('owner@demo.local'), findsOneWidget);
  });
}

FeePlan _samplePlan() {
  return FeePlan(
    id: 'p1',
    name: 'General',
    amountMinor: 150000,
    currency: 'INR',
    amountLabel: '₹1,500.00',
    billingCycle: 'MONTHLY',
    graceDays: 0,
    isDefault: true,
  );
}

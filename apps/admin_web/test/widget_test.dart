import 'package:feesaas_admin_web/app.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('admin login is shown', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: AdminApp()));
    await tester.pump();
    expect(find.text('Platform console'), findsOneWidget);
  });
}

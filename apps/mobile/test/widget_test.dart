import 'package:feesaas_mobile/app.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('login screen is the first surface', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: FeeSaasApp()));
    await tester.pump();
    expect(find.text('Sign in to see who owes you money.'), findsOneWidget);
    expect(find.text('Sign in'), findsOneWidget);
    expect(find.text('Forgot password?'), findsOneWidget);
  });
}

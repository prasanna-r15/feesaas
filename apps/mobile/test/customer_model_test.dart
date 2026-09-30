import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Customer.fromJson keeps due date', () {
    final person = Customer.fromJson({
      'id': 'c1',
      'customerCode': 'C0001',
      'fullName': 'Rahul Sharma',
      'phone': '+919800000001',
      'status': 'ACTIVE',
      'dueDate': '2026-10-15',
    });
    expect(person.dueDate, '2026-10-15');
    expect(person.isOverdue, isFalse);
  });

  test('Customer.isOverdue when due date is in the past', () {
    final person = Customer(
      id: 'c1',
      customerCode: 'C0001',
      fullName: 'Late',
      status: 'ACTIVE',
      dueDate: '2020-01-01',
    );
    expect(person.isOverdue, isTrue);
  });

  test('PendingFee.fromJson maps outstanding label', () {
    final fee = PendingFee.fromJson({
      'id': 'f1',
      'customerId': 'c1',
      'customerName': 'Rahul Sharma',
      'customerCode': 'C0001',
      'dueDate': '2026-10-15',
      'grossMinor': 150000,
      'paidMinor': 0,
      'outstandingMinor': 150000,
      'currency': 'INR',
      'outstandingLabel': '₹1,500.00',
      'status': 'PENDING',
      'effectiveStatus': 'OVERDUE',
      'planName': 'Monthly fee',
    });
    expect(fee.outstandingLabel, '₹1,500.00');
    expect(fee.isOverdue, isTrue);
    expect(fee.hasWhatsapp, isTrue);
  });

  test('RemindPayload prefers SMS when member has no WhatsApp', () {
    final payload = RemindPayload.fromJson({
      'channel': 'SMS',
      'body': 'Hi',
      'waLink': 'https://wa.me/919800000001?text=Hi',
      'smsLink': 'sms:+919800000001?body=Hi',
    });
    expect(payload.preferWhatsapp, isFalse);
  });

  test('FeePlan.fromJson keeps amount label', () {
    final plan = FeePlan.fromJson({
      'id': 'p1',
      'name': 'PT',
      'amountMinor': 500000,
      'currency': 'INR',
      'amountLabel': '₹5,000.00',
      'billingCycle': 'MONTHLY',
      'graceDays': 0,
      'isDefault': false,
    });
    expect(plan.name, 'PT');
    expect(plan.amountMinor, 500000);
  });
}

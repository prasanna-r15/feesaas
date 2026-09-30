import 'package:feesaas_api_client/feesaas_api_client.dart';
import 'package:feesaas_core/src/network/dio_factory.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final authApiProvider = Provider<AuthApi>((ref) => AuthApi(ref.watch(dioProvider)));
final customerApiProvider = Provider<CustomerApi>((ref) => CustomerApi(ref.watch(dioProvider)));
final feeApiProvider = Provider<FeeApi>((ref) => FeeApi(ref.watch(dioProvider)));
final settingsApiProvider = Provider<SettingsApi>((ref) => SettingsApi(ref.watch(dioProvider)));
final reportApiProvider = Provider<ReportApi>((ref) => ReportApi(ref.watch(dioProvider)));
final paymentApiProvider = Provider<PaymentApi>((ref) => PaymentApi(ref.watch(dioProvider)));
final attendanceApiProvider = Provider<AttendanceApi>((ref) => AttendanceApi(ref.watch(dioProvider)));
final staffApiProvider = Provider<StaffApi>((ref) => StaffApi(ref.watch(dioProvider)));
final platformApiProvider = Provider<PlatformApi>((ref) => PlatformApi(ref.watch(dioProvider)));
final personalApiProvider = Provider<PersonalApi>((ref) => PersonalApi(ref.watch(dioProvider)));
final groupsApiProvider = Provider<GroupsApi>((ref) => GroupsApi(ref.watch(dioProvider)));
final catalogApiProvider = Provider<CatalogApi>((ref) => CatalogApi(ref.watch(dioProvider)));

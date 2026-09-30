import 'package:dio/dio.dart';
import 'package:feesaas_api_client/src/models.dart';

class ReportApi {
  ReportApi(this._dio);

  final Dio _dio;

  Future<ReportOverview> overview() async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/reports/overview');
    return ReportOverview.fromJson(response.data!);
  }

  Future<({String filename, List<int> bytes})> export({String format = 'xlsx'}) async {
    final response = await _dio.get<List<int>>(
      '/api/v1/reports/export',
      queryParameters: {'format': format},
      options: Options(responseType: ResponseType.bytes),
    );
    final header = response.headers.value('content-disposition') ?? '';
    var filename = format == 'csv' ? 'duemate-collections.csv' : 'duemate-collections.xlsx';
    final match = RegExp(r'filename="?([^"]+)"?').firstMatch(header);
    if (match != null) {
      filename = match.group(1)!;
    }
    return (filename: filename, bytes: response.data ?? const []);
  }
}

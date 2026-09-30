class ApiProblem implements Exception {
  ApiProblem({required this.status, required this.code, required this.detail});

  final int status;
  final String code;
  final String detail;

  factory ApiProblem.fromJson(int status, Map<String, dynamic> json) {
    return ApiProblem(
      status: status,
      code: (json['code'] as String?) ?? 'INTERNAL_ERROR',
      detail: (json['detail'] as String?) ?? (json['title'] as String?) ?? 'Request failed',
    );
  }

  @override
  String toString() => '$code: $detail';
}

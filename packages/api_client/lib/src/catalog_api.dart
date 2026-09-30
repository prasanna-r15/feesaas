import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:feesaas_api_client/src/models.dart';

class CatalogApi {
  CatalogApi(this._dio);

  final Dio _dio;

  Future<List<SiteBranch>> listBranches() => _list('/api/v1/branches', SiteBranch.fromJson);

  Future<List<SiteBranch>> createBranch({required String name, String? address, String? phone, bool primary = false}) {
    return _postList('/api/v1/branches', {
      'name': name,
      if (address != null) 'address': address,
      if (phone != null) 'phone': phone,
      'primary': primary,
    }, SiteBranch.fromJson);
  }

  Future<List<SiteBranch>> deleteBranch(String id) => _deleteList('/api/v1/branches/$id', SiteBranch.fromJson);

  Future<List<AddonProduct>> listAddons() => _list('/api/v1/addons', AddonProduct.fromJson);

  Future<List<AddonProduct>> createAddon({required String name, String? description, required int amountMinor}) {
    return _postList('/api/v1/addons', {
      'name': name,
      if (description != null) 'description': description,
      'amountMinor': amountMinor,
      'currency': 'INR',
    }, AddonProduct.fromJson);
  }

  Future<List<AddonProduct>> deleteAddon(String id) => _deleteList('/api/v1/addons/$id', AddonProduct.fromJson);

  Future<List<AddonSale>> listSales() => _list('/api/v1/addon-sales', AddonSale.fromJson);

  Future<List<AddonSale>> collect({
    required String productId,
    required String customerId,
    int? amountMinor,
    String method = 'CASH',
    String? notes,
  }) {
    return _postList('/api/v1/addons/$productId/collect', {
      'customerId': customerId,
      if (amountMinor != null) 'amountMinor': amountMinor,
      'method': method,
      if (notes != null) 'notes': notes,
    }, AddonSale.fromJson);
  }

  Future<List<DietChart>> listDiets() => _list('/api/v1/diet-charts', DietChart.fromJson);

  Future<List<DietChart>> createDiet({required String name, required String body}) {
    return _postList('/api/v1/diet-charts', {'name': name, 'body': body}, DietChart.fromJson);
  }

  Future<List<DietChart>> deleteDiet(String id) => _deleteList('/api/v1/diet-charts/$id', DietChart.fromJson);

  Future<List<RemindPayload>> sendDiet({
    required String templateId,
    List<String> customerIds = const [],
    String? branchId,
  }) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/diet-charts/$templateId/send',
      data: jsonEncode({
        'customerIds': customerIds,
        if (branchId != null && branchId.isNotEmpty) 'branchId': branchId,
      }),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    final sends = (response.data?['sends'] as List<dynamic>? ?? const []);
    return sends.map((e) => RemindPayload.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  Future<List<T>> _list<T>(String path, T Function(Map<String, dynamic>) parse) async {
    final response = await _dio.get<List<dynamic>>(path);
    return (response.data ?? const []).map((e) => parse(Map<String, dynamic>.from(e as Map))).toList();
  }

  Future<List<T>> _postList<T>(String path, Map<String, Object?> data, T Function(Map<String, dynamic>) parse) async {
    final response = await _dio.post<List<dynamic>>(
      path,
      data: jsonEncode(data),
      options: Options(contentType: Headers.jsonContentType, responseType: ResponseType.json),
    );
    return (response.data ?? const []).map((e) => parse(Map<String, dynamic>.from(e as Map))).toList();
  }

  Future<List<T>> _deleteList<T>(String path, T Function(Map<String, dynamic>) parse) async {
    final response = await _dio.delete<List<dynamic>>(path);
    return (response.data ?? const []).map((e) => parse(Map<String, dynamic>.from(e as Map))).toList();
  }
}

class SiteBranch {
  SiteBranch({required this.id, required this.name, this.address, this.phone, this.primary = false});

  final String id;
  final String name;
  final String? address;
  final String? phone;
  final bool primary;

  factory SiteBranch.fromJson(Map<String, dynamic> json) {
    return SiteBranch(
      id: json['id'] as String,
      name: json['name'] as String,
      address: (json['address'] as String?)?.isEmpty == true ? null : json['address'] as String?,
      phone: (json['phone'] as String?)?.isEmpty == true ? null : json['phone'] as String?,
      primary: json['primary'] as bool? ?? false,
    );
  }
}

class AddonProduct {
  AddonProduct({
    required this.id,
    required this.name,
    this.description,
    required this.amountMinor,
    required this.currency,
    this.active = true,
  });

  final String id;
  final String name;
  final String? description;
  final int amountMinor;
  final String currency;
  final bool active;

  String get amountLabel => '₹${(amountMinor / 100).toStringAsFixed(2)}';

  factory AddonProduct.fromJson(Map<String, dynamic> json) {
    return AddonProduct(
      id: json['id'] as String,
      name: json['name'] as String,
      description: (json['description'] as String?)?.isEmpty == true ? null : json['description'] as String?,
      amountMinor: (json['amountMinor'] as num).toInt(),
      currency: json['currency'] as String? ?? 'INR',
      active: json['active'] as bool? ?? true,
    );
  }
}

class AddonSale {
  AddonSale({
    required this.id,
    required this.productName,
    required this.customerName,
    required this.amountMinor,
    required this.method,
    required this.soldOn,
  });

  final String id;
  final String productName;
  final String customerName;
  final int amountMinor;
  final String method;
  final String soldOn;

  factory AddonSale.fromJson(Map<String, dynamic> json) {
    return AddonSale(
      id: json['id'] as String,
      productName: json['productName'] as String,
      customerName: json['customerName'] as String,
      amountMinor: (json['amountMinor'] as num).toInt(),
      method: json['method'] as String,
      soldOn: json['soldOn'] as String,
    );
  }
}

class DietChart {
  DietChart({required this.id, required this.name, required this.body});

  final String id;
  final String name;
  final String body;

  factory DietChart.fromJson(Map<String, dynamic> json) {
    return DietChart(
      id: json['id'] as String,
      name: json['name'] as String,
      body: json['body'] as String,
    );
  }
}

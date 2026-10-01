import 'package:feesaas_admin_web/router.dart';
import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class AdminApp extends ConsumerWidget {
  const AdminApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(adminRouterProvider);
    ref.watch(platformSupportUnreadProvider);
    return MaterialApp.router(
      title: 'DueMate Admin',
      debugShowCheckedModeBanner: false,
      checkerboardOffscreenLayers: false,
      checkerboardRasterCacheImages: false,
      showPerformanceOverlay: false,
      theme: FsTheme.light(),
      routerConfig: router,
    );
  }
}

import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/router/app_router.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class FeeSaasApp extends ConsumerWidget {
  const FeeSaasApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(appRouterProvider);
    final config = ref.watch(tenantConfigProvider);
    final accent = parseAccent(config?.tenant?.accentColor);
    return MaterialApp.router(
      title: config?.tenant?.title ?? 'DueMate',
      debugShowCheckedModeBanner: false,
      checkerboardOffscreenLayers: false,
      checkerboardRasterCacheImages: false,
      showPerformanceOverlay: false,
      theme: FsTheme.light(accent: accent),
      routerConfig: router,
    );
  }
}

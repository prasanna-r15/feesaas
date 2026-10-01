import 'dart:async';

import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final mySupportProvider = FutureProvider.autoDispose<List<SupportMessage>>((ref) {
  return ref.watch(authApiProvider).listMySupport();
});

class SupportChatScreen extends ConsumerStatefulWidget {
  const SupportChatScreen({super.key});

  @override
  ConsumerState<SupportChatScreen> createState() => _SupportChatScreenState();
}

class _SupportChatScreenState extends ConsumerState<SupportChatScreen> {
  final _body = TextEditingController();
  final _scroll = ScrollController();
  Timer? _poll;
  var _sending = false;
  String? _lastId;

  @override
  void initState() {
    super.initState();
    _poll = Timer.periodic(const Duration(seconds: 2), (_) {
      if (mounted) {
        ref.invalidate(mySupportProvider);
        ref.read(mySupportUnreadProvider.notifier).refresh();
      }
    });
  }

  @override
  void dispose() {
    _poll?.cancel();
    _body.dispose();
    _scroll.dispose();
    super.dispose();
  }

  void _follow(List<SupportMessage> rows) {
    if (rows.isEmpty) {
      return;
    }
    final last = rows.last.id;
    if (last == _lastId) {
      return;
    }
    _lastId = last;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) {
        _scroll.jumpTo(_scroll.position.maxScrollExtent);
      }
    });
  }

  Future<void> _send() async {
    final text = _body.text.trim();
    if (text.isEmpty) {
      return;
    }
    setState(() => _sending = true);
    try {
      await ref.read(authApiProvider).sendMySupport(text);
      _body.clear();
      ref.invalidate(mySupportProvider);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(problemOf(e).detail)));
      }
    } finally {
      if (mounted) {
        setState(() => _sending = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    ref.listen(mySupportProvider, (_, next) {
      final rows = next.valueOrNull;
      if (rows != null) {
        _follow(rows);
      }
    });
    final async = ref.watch(mySupportProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Chat with admin')),
      body: Column(
        children: [
          Expanded(
            child: async.when(
              skipLoadingOnReload: true,
              skipLoadingOnRefresh: true,
              loading: () => const FsLoading(),
              error: (e, _) => FsErrorState(
                message: problemOf(e).detail,
                onRetry: () => ref.invalidate(mySupportProvider),
              ),
              data: (rows) {
                if (rows.isEmpty) {
                  return const FsEmptyState(
                    title: 'Say hello',
                    message: 'DueMate admin will reply here. Messages refresh live while this screen is open.',
                  );
                }
                return ListView.builder(
                  controller: _scroll,
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
                  itemCount: rows.length,
                  itemBuilder: (context, i) {
                    final m = rows[i];
                    final mine = !m.fromPlatform;
                    return Align(
                      alignment: mine ? Alignment.centerRight : Alignment.centerLeft,
                      child: Container(
                        margin: const EdgeInsets.only(bottom: 8),
                        padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
                        constraints: const BoxConstraints(maxWidth: 420),
                        decoration: BoxDecoration(
                          color: mine
                              ? Theme.of(context).colorScheme.primaryContainer
                              : Theme.of(context).colorScheme.surfaceContainerHighest,
                          borderRadius: BorderRadius.circular(14),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(m.body),
                            if (m.createdAt != null && m.createdAt!.isNotEmpty)
                              Padding(
                                padding: const EdgeInsets.only(top: 4),
                                child: Text(
                                  formatLocalDateTime(m.createdAt),
                                  style: Theme.of(context).textTheme.bodySmall,
                                ),
                              ),
                          ],
                        ),
                      ),
                    );
                  },
                );
              },
            ),
          ),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _body,
                      minLines: 1,
                      maxLines: 4,
                      enabled: !_sending,
                      decoration: const InputDecoration(hintText: 'Message admin'),
                    ),
                  ),
                  IconButton(
                    onPressed: _sending ? null : _send,
                    icon: const Icon(Icons.send),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

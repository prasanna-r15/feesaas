import 'dart:async';

import 'package:feesaas_admin_web/admin_app_bar.dart';
import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class SupportInboxPage extends ConsumerStatefulWidget {
  const SupportInboxPage({super.key});

  @override
  ConsumerState<SupportInboxPage> createState() => _SupportInboxPageState();
}

class _SupportInboxPageState extends ConsumerState<SupportInboxPage> {
  Timer? _poll;
  List<SupportThread> _rows = const [];
  Object? _error;
  var _loading = true;

  @override
  void initState() {
    super.initState();
    _refresh();
    _poll = Timer.periodic(const Duration(seconds: 2), (_) => _refresh(silent: true));
  }

  @override
  void dispose() {
    _poll?.cancel();
    super.dispose();
  }

  Future<void> _refresh({bool silent = false}) async {
    try {
      final rows = await ref.read(platformApiProvider).listSupportThreads();
      if (mounted) {
        setState(() {
          _rows = rows;
          _error = null;
          _loading = false;
        });
        ref.read(platformSupportUnreadProvider.notifier).refresh();
      }
    } catch (e) {
      if (mounted && !silent) {
        setState(() {
          _error = e;
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AdminScaffold(
      title: 'Support chat',
      body: _loading
          ? const FsLoading(personal: false)
          : _error != null
              ? FsErrorState(message: problemOf(_error!).detail, onRetry: _refresh)
              : _rows.isEmpty
                  ? const FsEmptyState(
                      title: 'No chats yet',
                      message: 'When an individual sends a business enquiry or messages admin, it shows up here.',
                    )
                  : ListView.separated(
                      padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
                      itemCount: _rows.length,
                      separatorBuilder: (_, __) => const SizedBox(height: 10),
                      itemBuilder: (context, i) {
                        final t = _rows[i];
                        return FsCard(
                          child: ListTile(
                            leading: FsUnreadBadge(
                              count: t.unreadCount,
                              child: const Icon(Icons.chat_outlined),
                            ),
                            title: Text(
                              t.fullName.isEmpty ? t.email ?? t.userId : t.fullName,
                              style: TextStyle(fontWeight: t.unreadCount > 0 ? FontWeight.w800 : FontWeight.w600),
                            ),
                            subtitle: Text(
                              [
                                t.email ?? '',
                                t.lastBody ?? '',
                                if (t.lastAt != null && t.lastAt!.isNotEmpty) formatLocalDateTime(t.lastAt),
                              ].where((e) => e.isNotEmpty).join(' · '),
                            ),
                            onTap: () => context.push('/support/${t.id}'),
                          ),
                        );
                      },
                    ),
    );
  }
}

class SupportThreadPage extends ConsumerStatefulWidget {
  const SupportThreadPage({super.key, required this.threadId});

  final String threadId;

  @override
  ConsumerState<SupportThreadPage> createState() => _SupportThreadPageState();
}

class _SupportThreadPageState extends ConsumerState<SupportThreadPage> {
  final _body = TextEditingController();
  final _scroll = ScrollController();
  Timer? _poll;
  List<SupportMessage> _rows = const [];
  Object? _error;
  var _loading = true;
  var _sending = false;
  String? _lastId;

  @override
  void initState() {
    super.initState();
    _refresh();
    _poll = Timer.periodic(const Duration(seconds: 2), (_) => _refresh(silent: true));
  }

  @override
  void dispose() {
    _poll?.cancel();
    _body.dispose();
    _scroll.dispose();
    super.dispose();
  }

  void _follow() {
    if (_rows.isEmpty) {
      return;
    }
    final last = _rows.last.id;
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

  Future<void> _refresh({bool silent = false}) async {
    try {
      final rows = await ref.read(platformApiProvider).listSupportMessages(widget.threadId);
      if (!mounted) {
        return;
      }
      setState(() {
        _rows = rows;
        _error = null;
        _loading = false;
      });
      _follow();
      ref.read(platformSupportUnreadProvider.notifier).refresh();
    } catch (e) {
      if (mounted && !silent) {
        setState(() {
          _error = e;
          _loading = false;
        });
      }
    }
  }

  Future<void> _send() async {
    final text = _body.text.trim();
    if (text.isEmpty) {
      return;
    }
    setState(() => _sending = true);
    try {
      await ref.read(platformApiProvider).sendSupportReply(widget.threadId, text);
      _body.clear();
      await _refresh();
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
    return AdminScaffold(
      title: 'Reply',
      body: Column(
        children: [
          Expanded(
            child: _loading
                ? const FsLoading(personal: false)
                : _error != null
                    ? FsErrorState(message: problemOf(_error!).detail, onRetry: _refresh)
                    : _rows.isEmpty
                        ? const FsEmptyState(title: 'No messages yet')
                        : ListView.builder(
                            controller: _scroll,
                            padding: const EdgeInsets.all(16),
                            itemCount: _rows.length,
                            itemBuilder: (context, i) {
                              final m = _rows[i];
                              final mine = m.fromPlatform;
                              return Align(
                                alignment: mine ? Alignment.centerRight : Alignment.centerLeft,
                                child: Container(
                                  margin: const EdgeInsets.only(bottom: 8),
                                  padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
                                  constraints: const BoxConstraints(maxWidth: 520),
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
                          ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _body,
                    minLines: 1,
                    maxLines: 4,
                    decoration: const InputDecoration(hintText: 'Reply as admin'),
                    onSubmitted: (_) => _send(),
                  ),
                ),
                IconButton(onPressed: _sending ? null : _send, icon: const Icon(Icons.send)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/features/groups/group_list_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class GroupInviteScreen extends ConsumerStatefulWidget {
  const GroupInviteScreen({super.key, required this.token});

  final String token;

  @override
  ConsumerState<GroupInviteScreen> createState() => _GroupInviteScreenState();
}

class _GroupInviteScreenState extends ConsumerState<GroupInviteScreen> {
  String? _error;
  GroupInvitePreview? _preview;
  var _loading = true;
  var _joining = false;

  String get _next => '/group/invite/${widget.token}';

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final preview = await ref.read(groupsApiProvider).previewInvite(widget.token);
      if (mounted) {
        setState(() {
          _preview = preview;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = problemOf(e).detail;
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(sessionControllerProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Group invite')),
      body: _loading
          ? const FsLoading()
          : ListView(
              padding: const EdgeInsets.all(24),
              children: [
                if (_preview != null) ...[
                  Text(_preview!.groupName, style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
                  const SizedBox(height: 8),
                  Text(
                    '${_preview!.memberCount} ${_preview!.memberCount == 1 ? 'person' : 'people'} already in this group',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 6),
                  Text('Invited by ${_preview!.invitedBy} · ${_preview!.groupType}'),
                  const SizedBox(height: 12),
                  const Text('You’ll see every split and who still needs to pay. Only the person who created a split can edit it.'),
                ] else
                  FsEmptyState(title: _error ?? 'This invite is not valid.'),
                if (_error != null && _preview != null) ...[
                  const SizedBox(height: 12),
                  Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                ],
                const SizedBox(height: 24),
                if (session.status != AuthStatus.signedIn) ...[
                  FilledButton(
                    onPressed: () => context.go('/login?next=${Uri.encodeComponent(_next)}'),
                    child: const Text('Sign in to join'),
                  ),
                  TextButton(
                    onPressed: () => context.go('/signup?next=${Uri.encodeComponent(_next)}'),
                    child: const Text('Create account'),
                  ),
                ] else if (_preview != null)
                  FilledButton(
                    onPressed: _joining
                        ? null
                        : () async {
                            setState(() => _joining = true);
                            try {
                              await ref.read(groupsApiProvider).acceptInvite(widget.token);
                              ref.invalidate(groupsListProvider);
                              if (context.mounted) context.go('/groups/${_preview!.groupId}');
                            } catch (e) {
                              setState(() {
                                _error = problemOf(e).detail;
                                _joining = false;
                              });
                            }
                          },
                    child: Text(_joining ? 'Joining…' : 'Join ${_preview!.groupName}'),
                  ),
              ],
            ),
    );
  }
}

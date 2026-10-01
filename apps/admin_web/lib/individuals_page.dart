import 'package:feesaas_admin_web/admin_app_bar.dart';
import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final individualQueryProvider = StateProvider<String>((ref) => '');

final individualsProvider = FutureProvider.autoDispose<List<IndividualAccount>>((ref) {
  final q = ref.watch(individualQueryProvider);
  return ref.watch(platformApiProvider).listIndividuals(q: q);
});

class IndividualsPage extends ConsumerStatefulWidget {
  const IndividualsPage({super.key});

  @override
  ConsumerState<IndividualsPage> createState() => _IndividualsPageState();
}

class _IndividualsPageState extends ConsumerState<IndividualsPage> {
  final _search = TextEditingController();

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(individualsProvider);
    return AdminScaffold(
      title: 'Individuals',
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: TextField(
              controller: _search,
              decoration: const InputDecoration(
                prefixIcon: Icon(Icons.search),
                labelText: 'Search name, email, or phone',
              ),
              onChanged: (v) => ref.read(individualQueryProvider.notifier).state = v,
            ),
          ),
          Expanded(
            child: async.when(
              loading: () => const FsLoading(),
              error: (e, _) => FsErrorState(
                message: problemOf(e).detail,
                onRetry: () => ref.invalidate(individualsProvider),
              ),
              data: (rows) {
                if (rows.isEmpty) {
                  return const FsEmptyState(
                    title: 'No individual accounts',
                    message: 'People who signed up for personal / group expense tracking appear here.',
                  );
                }
                return ListView.separated(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
                  itemCount: rows.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemBuilder: (context, i) {
                    final u = rows[i];
                    final login = u.lastLoginAt == null || u.lastLoginAt!.isEmpty
                        ? 'never logged in'
                        : 'last login ${formatLocalDateTime(u.lastLoginAt)}';
                    return FsCard(
                      child: ListTile(
                        title: Text(u.fullName, style: const TextStyle(fontWeight: FontWeight.w700)),
                        subtitle: Text(
                          [
                            u.email ?? '',
                            u.phone ?? '',
                            u.status,
                            login,
                            '${u.groupMemberCount} group seats',
                            '${u.groupsJoined} groups',
                            '${u.expenseCount} personal expenses',
                          ].where((e) => e.isNotEmpty).join(' · '),
                        ),
                        isThreeLine: true,
                      ),
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

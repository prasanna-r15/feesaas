import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final myEnquiriesProvider = FutureProvider.autoDispose<List<JoinEnquiry>>((ref) {
  return ref.watch(authApiProvider).listMyEnquiries();
});

class BusinessEnquiryScreen extends ConsumerStatefulWidget {
  const BusinessEnquiryScreen({super.key});

  @override
  ConsumerState<BusinessEnquiryScreen> createState() => _BusinessEnquiryScreenState();
}

class _BusinessEnquiryScreenState extends ConsumerState<BusinessEnquiryScreen> {
  final _business = TextEditingController();
  final _city = TextEditingController();
  final _message = TextEditingController();
  var _busy = false;
  String? _error;

  @override
  void dispose() {
    _business.dispose();
    _city.dispose();
    _message.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final name = _business.text.trim();
    if (name.isEmpty) {
      setState(() => _error = 'Enter the gym or business name.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result = await ref.read(authApiProvider).submitBusinessEnquiry(
            businessName: name,
            city: _city.text.trim(),
            message: _message.text.trim(),
          );
      if (!mounted) {
        return;
      }
      _message.clear();
      ref.invalidate(myEnquiriesProvider);
      final note = result.emailed
          ? 'Sent. Admin got an email, and you can send another enquiry below.'
          : 'Saved. You can send another enquiry below.';
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(note)));
    } catch (e) {
      setState(() => _error = problemOf(e).detail);
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final previous = ref.watch(myEnquiriesProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Business enquiry')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
        children: [
          previous.when(
            skipLoadingOnReload: true,
            skipLoadingOnRefresh: true,
            loading: () => const Padding(
              padding: EdgeInsets.only(bottom: 16),
              child: LinearProgressIndicator(),
            ),
            error: (e, _) => Padding(
              padding: const EdgeInsets.only(bottom: 16),
              child: Text(problemOf(e).detail, style: TextStyle(color: Theme.of(context).colorScheme.error)),
            ),
            data: (rows) {
              if (rows.isEmpty) {
                return const SizedBox.shrink();
              }
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Your enquiries', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
                  const SizedBox(height: 8),
                  for (final r in rows)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: FsCard(
                        child: ListTile(
                          title: Text(r.businessName, style: const TextStyle(fontWeight: FontWeight.w700)),
                          subtitle: Text(
                            [
                              r.status,
                              if (r.city != null && r.city!.isNotEmpty) r.city!,
                              if (r.createdAt != null && r.createdAt!.isNotEmpty) formatLocalDateTime(r.createdAt),
                              if (r.message != null && r.message!.isNotEmpty) r.message!,
                            ].join(' · '),
                          ),
                          isThreeLine: r.message != null && r.message!.isNotEmpty,
                        ),
                      ),
                    ),
                  const SizedBox(height: 8),
                  Text(
                    'Send another enquiry',
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800),
                  ),
                  const SizedBox(height: 8),
                ],
              );
            },
          ),
          const Text('Tell admin the gym, yoga studio, or business you want to run. They will set it up and you become the owner.'),
          const SizedBox(height: 16),
          TextField(
            controller: _business,
            enabled: !_busy,
            textCapitalization: TextCapitalization.words,
            decoration: const InputDecoration(labelText: 'Business / gym name'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _city,
            enabled: !_busy,
            textCapitalization: TextCapitalization.words,
            decoration: const InputDecoration(labelText: 'City (optional)'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _message,
            enabled: !_busy,
            minLines: 4,
            maxLines: 8,
            decoration: const InputDecoration(labelText: 'Message (optional)'),
          ),
          if (_error != null) ...[
            const SizedBox(height: 12),
            Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ],
          const SizedBox(height: 24),
          FilledButton(
            onPressed: _busy ? null : _submit,
            child: Text(_busy ? 'Sending…' : 'Send enquiry'),
          ),
        ],
      ),
    );
  }
}

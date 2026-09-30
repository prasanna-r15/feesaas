import 'package:feesaas_core/feesaas_core.dart';
import 'package:feesaas_mobile/util/files.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class ImportMembersScreen extends ConsumerStatefulWidget {
  const ImportMembersScreen({super.key});

  @override
  ConsumerState<ImportMembersScreen> createState() => _ImportMembersScreenState();
}

class _ImportMembersScreenState extends ConsumerState<ImportMembersScreen> {
  bool _busy = false;
  ImportResult? _result;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Import members')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text(
            'Download the Excel template, fill one member per row (phone as text, due date YYYY-MM-DD, Branch as the location name such as Karamadai). Existing phone or email is skipped.',
          ),
          const SizedBox(height: 16),
          FilledButton.tonal(
            onPressed: _busy ? null : _downloadTemplate,
            child: const Text('Download template'),
          ),
          const SizedBox(height: 12),
          OutlinedButton(
            onPressed: _busy ? null : _exportMembers,
            child: const Text('Export current members'),
          ),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: _busy ? null : _upload,
            child: Text(_busy ? 'Importing…' : 'Upload Excel'),
          ),
          if (_result != null) ...[
            const SizedBox(height: 24),
            Text('Created ${_result!.created}, skipped ${_result!.skipped}.'),
            ..._result!.errors.map((e) => Text(e, style: const TextStyle(color: Colors.red))),
          ],
        ],
      ),
    );
  }

  Future<void> _downloadTemplate() async {
    try {
      final bytes = await ref.read(customerApiProvider).downloadTemplate();
      if (bytes.isEmpty) {
        throw StateError('Template download was empty. Check you are signed in.');
      }
      final path = await saveBytes('duemate-members-template.xlsx', bytes);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Downloaded $path')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    }
  }

  Future<void> _exportMembers() async {
    try {
      final bytes = await ref.read(customerApiProvider).exportXlsx();
      final path = await saveBytes('duemate-members.xlsx', bytes);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Downloaded $path')));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    }
  }

  Future<void> _upload() async {
    final file = await pickCsv();
    if (file == null) {
      return;
    }
    setState(() => _busy = true);
    try {
      final result = await ref.read(customerApiProvider).importCsv(file.bytes, filename: file.name);
      setState(() => _result = result);
      tickWorkspace(ref);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Imported ${result.created} members')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(problemOf(e).detail)));
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }
}

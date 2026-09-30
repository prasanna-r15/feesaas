import 'dart:convert';

import 'package:file_picker/file_picker.dart';
import 'package:feesaas_core/feesaas_core.dart';
import 'package:flutter/material.dart';

class TenantLogoField extends StatelessWidget {
  const TenantLogoField({
    super.key,
    required this.dataUri,
    required this.onChanged,
  });

  final String? dataUri;
  final ValueChanged<String?> onChanged;

  Future<void> _pick(BuildContext context) async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: const ['png', 'jpg', 'jpeg', 'webp', 'gif'],
      withData: true,
    );
    if (result == null || result.files.isEmpty) {
      return;
    }
    final file = result.files.first;
    final bytes = file.bytes;
    if (bytes == null) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Could not read that image. Try another file.')),
        );
      }
      return;
    }
    if (bytes.length > 1048576) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Logo must be smaller than 1 MB.')),
        );
      }
      return;
    }
    final ext = (file.extension ?? 'png').toLowerCase();
    final mime = switch (ext) {
      'jpg' || 'jpeg' => 'image/jpeg',
      'webp' => 'image/webp',
      'gif' => 'image/gif',
      _ => 'image/png',
    };
    onChanged('data:$mime;base64,${base64Encode(bytes)}');
  }

  @override
  Widget build(BuildContext context) {
    final bytes = DueMateLogo.decodeBytes(dataUri);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Logo', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 4),
        Text(
          'PNG, JPEG, WebP, or GIF. Under 1 MB. Shown after gym staff sign in.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 12),
        if (bytes != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: Image.memory(bytes, height: 72, fit: BoxFit.contain),
          ),
        Wrap(
          spacing: 8,
          children: [
            OutlinedButton.icon(
              onPressed: () => _pick(context),
              icon: const Icon(Icons.image_outlined),
              label: Text(dataUri == null ? 'Choose logo' : 'Replace logo'),
            ),
            if (dataUri != null)
              TextButton(
                onPressed: () => onChanged(null),
                child: const Text('Remove'),
              ),
          ],
        ),
      ],
    );
  }
}

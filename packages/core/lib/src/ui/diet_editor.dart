import 'package:flutter/material.dart';

/// Compact document editor for diet charts. Stores simple HTML; WhatsApp uses the plain-text form.
class DietEditor extends StatelessWidget {
  const DietEditor({
    super.key,
    required this.controller,
    this.minLines = 10,
    this.label = 'Diet chart',
  });

  final TextEditingController controller;
  final int minLines;
  final String label;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Wrap(
          spacing: 4,
          children: [
            _Tool(icon: Icons.title, tooltip: 'Heading', onTap: () => _wrap(controller, '<h3>', '</h3>')),
            _Tool(icon: Icons.format_bold, tooltip: 'Bold', onTap: () => _wrap(controller, '<b>', '</b>')),
            _Tool(icon: Icons.format_list_bulleted, tooltip: 'Bullet', onTap: () => _insert(controller, '\n• ')),
            _Tool(icon: Icons.horizontal_rule, tooltip: 'Divider', onTap: () => _insert(controller, '\n<hr/>\n')),
            _Tool(icon: Icons.notes, tooltip: 'Paragraph', onTap: () => _wrap(controller, '<p>', '</p>')),
          ],
        ),
        const SizedBox(height: 8),
        TextField(
          controller: controller,
          minLines: minLines,
          maxLines: 20,
          decoration: InputDecoration(
            labelText: label,
            alignLabelWithHint: true,
            filled: true,
            fillColor: scheme.surfaceContainerHighest.withValues(alpha: 0.35),
          ),
        ),
        const SizedBox(height: 12),
        Text('Preview', style: Theme.of(context).textTheme.labelLarge),
        const SizedBox(height: 6),
        ValueListenableBuilder<TextEditingValue>(
          valueListenable: controller,
          builder: (context, value, _) => DietDocumentView(html: value.text),
        ),
      ],
    );
  }

  static void _wrap(TextEditingController controller, String open, String close) {
    final sel = controller.selection;
    final text = controller.text;
    if (!sel.isValid) {
      controller.text = '$text$open$close';
      return;
    }
    final start = sel.start;
    final end = sel.end;
    final inner = text.substring(start, end);
    controller.value = TextEditingValue(
      text: text.replaceRange(start, end, '$open$inner$close'),
      selection: TextSelection.collapsed(offset: start + open.length + inner.length + close.length),
    );
  }

  static void _insert(TextEditingController controller, String snippet) {
    final sel = controller.selection;
    final offset = sel.isValid ? sel.baseOffset : controller.text.length;
    final text = controller.text;
    controller.value = TextEditingValue(
      text: text.replaceRange(offset, offset, snippet),
      selection: TextSelection.collapsed(offset: offset + snippet.length),
    );
  }
}

class DietDocumentView extends StatelessWidget {
  const DietDocumentView({super.key, required this.html, this.maxLines});

  final String html;
  final int? maxLines;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final blocks = _blocks(html);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: scheme.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: scheme.outlineVariant),
      ),
      child: blocks.isEmpty
          ? Text('Empty chart', style: TextStyle(color: scheme.onSurfaceVariant))
          : Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                for (final block in maxLines == null ? blocks : blocks.take(maxLines!))
                  Padding(
                    padding: const EdgeInsets.only(bottom: 6),
                    child: Text(
                      block.$2,
                      style: switch (block.$1) {
                        _DietKind.heading => Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800),
                        _DietKind.bullet => Theme.of(context).textTheme.bodyMedium,
                        _DietKind.rule => Theme.of(context).textTheme.bodySmall?.copyWith(color: scheme.outline),
                        _DietKind.body => Theme.of(context).textTheme.bodyMedium?.copyWith(height: 1.45),
                      },
                    ),
                  ),
              ],
            ),
    );
  }
}

enum _DietKind { heading, body, bullet, rule }

List<(_DietKind, String)> _blocks(String raw) {
  if (raw.trim().isEmpty) {
    return const [];
  }
  var s = raw
      .replaceAll(RegExp(r'<br\s*/?>', caseSensitive: false), '\n')
      .replaceAll(RegExp(r'</p>', caseSensitive: false), '\n')
      .replaceAll(RegExp(r'</div>', caseSensitive: false), '\n')
      .replaceAll(RegExp(r'</h[1-6]>', caseSensitive: false), '\n')
      .replaceAll(RegExp(r'</li>', caseSensitive: false), '\n')
      .replaceAll(RegExp(r'<li[^>]*>', caseSensitive: false), '• ')
      .replaceAll(RegExp(r'<hr[^>]*>', caseSensitive: false), '\n----------\n')
      .replaceAll(RegExp(r'<h[1-6][^>]*>', caseSensitive: false), '§ ')
      .replaceAll(RegExp(r'<[^>]+>'), '')
      .replaceAll('&nbsp;', ' ')
      .replaceAll('&amp;', '&');
  return s
      .split('\n')
      .map((l) => l.trim())
      .where((l) => l.isNotEmpty)
      .map((l) {
        if (l.startsWith('§ ')) {
          return (_DietKind.heading, l.substring(2));
        }
        if (l.startsWith('• ')) {
          return (_DietKind.bullet, l);
        }
        if (l.startsWith('---')) {
          return (_DietKind.rule, l);
        }
        return (_DietKind.body, l);
      })
      .toList();
}

class _Tool extends StatelessWidget {
  const _Tool({required this.icon, required this.tooltip, required this.onTap});

  final IconData icon;
  final String tooltip;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return IconButton.filledTonal(
      onPressed: onTap,
      icon: Icon(icon, size: 18),
      tooltip: tooltip,
      visualDensity: VisualDensity.compact,
    );
  }
}

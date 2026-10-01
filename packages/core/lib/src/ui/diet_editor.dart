import 'package:flutter/material.dart';

/// Diet charts are sent on WhatsApp, which ignores HTML/CSS.
/// Store WhatsApp markers: *bold*, _italic_, and pipe tables inside ```.
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
        Text(
          'WhatsApp formatting: *bold*, _italic_, • bullets. Tables use a monospace block '
          '(CSS from HTML will not show in WhatsApp).',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 4,
          children: [
            _Tool(icon: Icons.title, tooltip: 'Heading', onTap: () => _wrap(controller, '*', '*')),
            _Tool(icon: Icons.format_bold, tooltip: 'Bold', onTap: () => _wrap(controller, '*', '*')),
            _Tool(icon: Icons.format_italic, tooltip: 'Italic', onTap: () => _wrap(controller, '_', '_')),
            _Tool(icon: Icons.format_list_bulleted, tooltip: 'Bullet', onTap: () => _insert(controller, '\n• ')),
            _Tool(icon: Icons.table_chart_outlined, tooltip: 'Table', onTap: () => _insert(controller, _tableSnippet)),
            _Tool(icon: Icons.horizontal_rule, tooltip: 'Divider', onTap: () => _insert(controller, '\n----------\n')),
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
        Text('WhatsApp preview', style: Theme.of(context).textTheme.labelLarge),
        const SizedBox(height: 6),
        ValueListenableBuilder<TextEditingValue>(
          valueListenable: controller,
          builder: (context, value, _) => DietDocumentView(html: value.text),
        ),
      ],
    );
  }

  static const _tableSnippet = '''

```
*Time* | *Meal* | *Food*
---- | ---- | ----
8:00 am | Breakfast | Oats + egg
1:00 pm | Lunch | Rice + dal
```

''';

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
    List<(_DietKind, String)> blocks;
    try {
      blocks = _blocks(html);
    } catch (_) {
      blocks = [(_DietKind.body, html)];
    }
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
                    child: Text.rich(
                      TextSpan(children: _inlineSpans(context, block.$2, block.$1)),
                    ),
                  ),
              ],
            ),
    );
  }
}

enum _DietKind { heading, body, bullet, rule, table }

List<InlineSpan> _inlineSpans(BuildContext context, String line, _DietKind kind) {
  final base = switch (kind) {
    _DietKind.heading => Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800),
    _DietKind.table => Theme.of(context).textTheme.bodySmall?.copyWith(fontFamily: 'monospace', height: 1.45),
    _DietKind.rule => Theme.of(context).textTheme.bodySmall?.copyWith(color: Theme.of(context).colorScheme.outline),
    _DietKind.bullet || _DietKind.body => Theme.of(context).textTheme.bodyMedium?.copyWith(height: 1.45),
  };
  return _parseMarkers(line, base);
}

List<InlineSpan> _parseMarkers(String line, TextStyle? base) {
  final spans = <InlineSpan>[];
  final pattern = RegExp(r'\*([^*]+)\*|_([^_]+)_|~([^~]+)~');
  var start = 0;
  for (final match in pattern.allMatches(line)) {
    if (match.start > start) {
      spans.add(TextSpan(text: line.substring(start, match.start), style: base));
    }
    if (match.group(1) != null) {
      spans.add(TextSpan(text: match.group(1), style: base?.copyWith(fontWeight: FontWeight.w800)));
    } else if (match.group(2) != null) {
      spans.add(TextSpan(text: match.group(2), style: base?.copyWith(fontStyle: FontStyle.italic)));
    } else {
      spans.add(TextSpan(text: match.group(3), style: base?.copyWith(decoration: TextDecoration.lineThrough)));
    }
    start = match.end;
  }
  if (start < line.length) {
    spans.add(TextSpan(text: line.substring(start), style: base));
  }
  if (spans.isEmpty) {
    spans.add(TextSpan(text: line, style: base));
  }
  return spans;
}

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
      .replaceAll(RegExp(r'<h[1-6][^>]*>', caseSensitive: false), '*')
      .replaceAll(RegExp(r'</(?:strong|b)>', caseSensitive: false), '*')
      .replaceAll(RegExp(r'<(?:strong|b)[^>]*>', caseSensitive: false), '*')
      .replaceAll(RegExp(r'</(?:em|i)>', caseSensitive: false), '_')
      .replaceAll(RegExp(r'<(?:em|i)[^>]*>', caseSensitive: false), '_')
      .replaceAllMapped(RegExp(r'<table[^>]*>([\s\S]*?)</table>', caseSensitive: false), (m) {
        final inner = m.group(1) ?? '';
        final rows = RegExp(r'<tr[^>]*>([\s\S]*?)</tr>', caseSensitive: false).allMatches(inner).map((row) {
          final cells = RegExp(r'<t[dh][^>]*>([\s\S]*?)</t[dh]>', caseSensitive: false)
              .allMatches(row.group(1) ?? '')
              .map((c) => (c.group(1) ?? '').replaceAll(RegExp(r'<[^>]+>'), '').trim())
              .join(' | ');
          return cells;
        }).where((l) => l.isNotEmpty);
        return '\n```\n${rows.join('\n')}\n```\n';
      })
      .replaceAll(RegExp(r'<[^>]+>'), '')
      .replaceAll('&nbsp;', ' ')
      .replaceAll('&amp;', '&');
  final out = <(_DietKind, String)>[];
  var inTable = false;
  for (final rawLine in s.split('\n')) {
    final l = rawLine.trimRight();
    final t = l.trim();
    if (t == '```') {
      inTable = !inTable;
      continue;
    }
    if (t.isEmpty) {
      continue;
    }
    if (inTable) {
      out.add((_DietKind.table, t));
      continue;
    }
    if (t.startsWith('*') && t.endsWith('*') && t.length > 2 && !t.substring(1, t.length - 1).contains('*')) {
      out.add((_DietKind.heading, t.substring(1, t.length - 1)));
      continue;
    }
    if (t.startsWith('• ')) {
      out.add((_DietKind.bullet, t));
      continue;
    }
    if (t.startsWith('---')) {
      out.add((_DietKind.rule, t));
      continue;
    }
    out.add((_DietKind.body, t));
  }
  return out;
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

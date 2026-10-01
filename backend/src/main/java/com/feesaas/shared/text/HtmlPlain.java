package com.feesaas.shared.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns stored diet HTML into WhatsApp formatting. WhatsApp ignores CSS;
 * it accepts *bold*, _italic_, ~strike~, and monospace blocks for tables.
 */
public final class HtmlPlain {

    private static final Pattern TABLE = Pattern.compile("(?is)<table\\b[^>]*>(.*?)</table>");
    private static final Pattern TR = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>");
    private static final Pattern CELL = Pattern.compile("(?is)<t[dh]\\b[^>]*>(.*?)</t[dh]>");
    private static final Pattern HEADING = Pattern.compile("(?is)<h[1-6]\\b[^>]*>(.*?)</h[1-6]>");
    private static final Pattern BOLD = Pattern.compile("(?is)<(?:strong|b)\\b[^>]*>(.*?)</(?:strong|b)>");
    private static final Pattern ITALIC = Pattern.compile("(?is)<(?:em|i)\\b[^>]*>(.*?)</(?:em|i)>");
    private static final Pattern STRIKE = Pattern.compile("(?is)<(?:s|strike|del)\\b[^>]*>(.*?)</(?:s|strike|del)>");

    private HtmlPlain() {}

    public static String text(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String s = html;
        s = replaceAll(TABLE, s, m -> "\n```\n" + tableBody(m.group(1)) + "\n```\n");
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</p>", "\n");
        s = s.replaceAll("(?i)</div>", "\n");
        s = s.replaceAll("(?i)</li>", "\n");
        s = s.replaceAll("(?i)<li[^>]*>", "• ");
        s = s.replaceAll("(?i)<hr[^>]*>", "\n----------\n");
        s = replaceAll(HEADING, s, m -> "*" + inner(m.group(1)) + "*\n");
        s = replaceAll(BOLD, s, m -> "*" + inner(m.group(1)) + "*");
        s = replaceAll(ITALIC, s, m -> "_" + inner(m.group(1)) + "_");
        s = replaceAll(STRIKE, s, m -> "~" + inner(m.group(1)) + "~");
        s = s.replaceAll("<[^>]+>", "");
        s = s.replace("&nbsp;", " ");
        s = s.replace("&amp;", "&");
        s = s.replace("&lt;", "<");
        s = s.replace("&gt;", ">");
        s = s.replaceAll("[ \\t]+\\n", "\n");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    private static String tableBody(String inner) {
        Matcher rows = TR.matcher(inner == null ? "" : inner);
        List<String> lines = new ArrayList<>();
        boolean headerRule = false;
        while (rows.find()) {
            String rowHtml = rows.group(1);
            String line = cells(rowHtml);
            if (line.isBlank()) {
                continue;
            }
            lines.add(line);
            if (!headerRule && rowHtml.toLowerCase().contains("<th")) {
                lines.add(rule(line));
                headerRule = true;
            }
        }
        return String.join("\n", lines);
    }

    private static String cells(String rowHtml) {
        Matcher cells = CELL.matcher(rowHtml == null ? "" : rowHtml);
        List<String> values = new ArrayList<>();
        while (cells.find()) {
            values.add(inner(cells.group(1)).replace('\n', ' ').trim());
        }
        return String.join(" | ", values);
    }

    private static String rule(String headerLine) {
        int n = headerLine.split(" \\| ", -1).length;
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < Math.max(n, 1); i++) {
            parts.add("----");
        }
        return String.join(" | ", parts);
    }

    private static String inner(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim();
    }

    @FunctionalInterface
    private interface Replacer {
        String apply(Matcher matcher);
    }

    private static String replaceAll(Pattern pattern, String input, Replacer replacer) {
        Matcher m = pattern.matcher(input);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(replacer.apply(m)));
        }
        m.appendTail(out);
        return out.toString();
    }
}

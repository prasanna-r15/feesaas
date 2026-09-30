package com.feesaas.shared.text;

/** Turns stored diet HTML into WhatsApp/SMS plain text. */
public final class HtmlPlain {

    private HtmlPlain() {}

    public static String text(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String s = html;
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</p>", "\n");
        s = s.replaceAll("(?i)</div>", "\n");
        s = s.replaceAll("(?i)</h[1-6]>", "\n");
        s = s.replaceAll("(?i)</li>", "\n");
        s = s.replaceAll("(?i)<li[^>]*>", "• ");
        s = s.replaceAll("(?i)<hr[^>]*>", "\n----------\n");
        s = s.replaceAll("<[^>]+>", "");
        s = s.replace("&nbsp;", " ");
        s = s.replace("&amp;", "&");
        s = s.replace("&lt;", "<");
        s = s.replace("&gt;", ">");
        s = s.replaceAll("[ \\t]+\\n", "\n");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }
}

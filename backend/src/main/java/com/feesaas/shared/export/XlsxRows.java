package com.feesaas.shared.export;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class XlsxRows {

    private XlsxRows() {}

    public static boolean looksLikeZip(byte[] bytes) {
        return bytes != null && bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K';
    }

    public static List<List<String>> read(byte[] bytes) {
        Map<String, String> files = unzip(bytes);
        List<String> shared = parseSharedStrings(files.get("xl/sharedStrings.xml"));
        String sheet = files.entrySet().stream()
                .filter(e -> e.getKey().startsWith("xl/worksheets/") && e.getKey().endsWith(".xml"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No worksheet in the Excel file."));
        return parseSheet(sheet, shared);
    }

    public static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException ignored) {
        }
        for (DateTimeFormatter fmt : List.of(
                DateTimeFormatter.ofPattern("d/M/uuuu"),
                DateTimeFormatter.ofPattern("d-M-uuuu"),
                DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("M/d/uuuu"))) {
            try {
                return LocalDate.parse(v, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        try {
            double serial = Double.parseDouble(v.replace(",", ""));
            if (serial > 20000 && serial < 80000) {
                return LocalDate.of(1899, 12, 30).plusDays((long) serial);
            }
        } catch (NumberFormatException ignored) {
        }
        throw new DateTimeParseException("Not a date", v, 0);
    }

    public static String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String v = raw.trim();
        if (v.matches("(?i)\\d+(?:\\.\\d+)?e\\+\\d+")) {
            try {
                long n = Math.round(Double.parseDouble(v));
                return String.valueOf(n);
            } catch (NumberFormatException ignored) {
            }
        }
        if (v.endsWith(".0") && v.matches("\\d+\\.0")) {
            return v.substring(0, v.length() - 2);
        }
        return v;
    }

    private static Map<String, String> unzip(byte[] bytes) {
        Map<String, String> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read Excel file.", e);
        }
        return files;
    }

    private static List<String> parseSharedStrings(String xml) {
        List<String> out = new ArrayList<>();
        if (xml == null || xml.isBlank()) {
            return out;
        }
        Document doc = parseXml(xml);
        NodeList si = doc.getElementsByTagNameNS("*", "si");
        for (int i = 0; i < si.getLength(); i++) {
            out.add(si.item(i).getTextContent() == null ? "" : si.item(i).getTextContent());
        }
        return out;
    }

    private static List<List<String>> parseSheet(String xml, List<String> shared) {
        Document doc = parseXml(xml);
        NodeList rowNodes = doc.getElementsByTagNameNS("*", "row");
        List<List<String>> rows = new ArrayList<>();
        for (int r = 0; r < rowNodes.getLength(); r++) {
            Element row = (Element) rowNodes.item(r);
            NodeList cells = row.getElementsByTagNameNS("*", "c");
            Map<Integer, String> byCol = new HashMap<>();
            int max = -1;
            for (int c = 0; c < cells.getLength(); c++) {
                Element cell = (Element) cells.item(c);
                String ref = cell.getAttribute("r");
                int col = columnIndex(ref);
                max = Math.max(max, col);
                byCol.put(col, cellValue(cell, shared));
            }
            List<String> values = new ArrayList<>();
            for (int i = 0; i <= max; i++) {
                values.add(byCol.getOrDefault(i, ""));
            }
            rows.add(values);
        }
        return rows;
    }

    private static String cellValue(Element cell, List<String> shared) {
        String type = cell.getAttribute("t");
        if ("inlineStr".equals(type)) {
            return textOf(cell, "t");
        }
        String v = textOf(cell, "v");
        if ("s".equals(type) && !v.isBlank()) {
            int idx = Integer.parseInt(v);
            return idx >= 0 && idx < shared.size() ? shared.get(idx) : "";
        }
        return v;
    }

    private static String textOf(Element parent, String local) {
        NodeList nodes = parent.getElementsByTagNameNS("*", local);
        if (nodes.getLength() == 0) {
            return "";
        }
        String text = nodes.item(0).getTextContent();
        return text == null ? "" : text.trim();
    }

    private static int columnIndex(String ref) {
        if (ref == null || ref.isBlank()) {
            return 0;
        }
        int col = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                col = col * 26 + (ch - 'A' + 1);
            } else if (ch >= 'a' && ch <= 'z') {
                col = col * 26 + (ch - 'a' + 1);
            } else {
                break;
            }
        }
        return Math.max(col - 1, 0);
    }

    private static Document parseXml(String xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not parse Excel worksheet.", e);
        }
    }
}

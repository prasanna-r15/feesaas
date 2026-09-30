package com.feesaas.shared.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class Spreadsheets {

    private Spreadsheets() {}

    public static byte[] csv(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        appendCsvRow(sb, headers);
        for (List<String> row : rows) {
            appendCsvRow(sb, row);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] xlsx(String sheetName, List<String> headers, List<List<String>> rows) {
        return xlsx(sheetName, headers, rows, null, java.util.Set.of());
    }

    public static byte[] xlsx(
            String sheetName,
            List<String> headers,
            List<List<String>> rows,
            List<Double> columnWidths,
            java.util.Set<Integer> dateColumns) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                write(zip, "[Content_Types].xml", contentTypes());
                write(zip, "_rels/.rels", rels());
                write(zip, "xl/workbook.xml", workbook(sheetName));
                write(zip, "xl/_rels/workbook.xml.rels", workbookRels());
                write(zip, "xl/worksheets/sheet1.xml", sheet(headers, rows, columnWidths, dateColumns));
                write(zip, "xl/styles.xml", styles());
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build Excel file", e);
        }
    }

    private static void appendCsvRow(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(csvCell(cells.get(i)));
        }
        sb.append('\r').append('\n');
    }

    private static String csvCell(String value) {
        String v = value == null ? "" : value;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static void write(ZipOutputStream zip, String name, String xml) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                </Types>
                """;
    }

    private static String rels() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """;
    }

    private static String workbook(String name) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="%s" sheetId="1" r:id="rId1"/>
                  </sheets>
                </workbook>
                """.formatted(xml(name == null || name.isBlank() ? "Sheet1" : name));
    }

    private static String workbookRels() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>
                """;
    }

    private static String styles() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <numFmts count="2">
                    <numFmt numFmtId="164" formatCode="yyyy-mm-dd"/>
                    <numFmt numFmtId="165" formatCode="@"/>
                  </numFmts>
                  <fonts count="2">
                    <font><sz val="11"/><color theme="1"/><name val="Calibri"/></font>
                    <font><b/><sz val="12"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
                  </fonts>
                  <fills count="3">
                    <fill><patternFill patternType="none"/></fill>
                    <fill><patternFill patternType="gray125"/></fill>
                    <fill><patternFill patternType="solid"><fgColor rgb="FF0F766E"/><bgColor indexed="64"/></patternFill></fill>
                  </fills>
                  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
                  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                  <cellXfs count="4">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1">
                      <alignment horizontal="center" vertical="center"/>
                    </xf>
                    <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
                    <xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
                  </cellXfs>
                </styleSheet>
                """;
    }

    private static String sheet(
            List<String> headers,
            List<List<String>> rows,
            List<Double> columnWidths,
            java.util.Set<Integer> dateColumns) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                """);
        sb.append(colsXml(headers.size(), columnWidths, dateColumns));
        sb.append("<sheetData>");
        writeRow(sb, 1, headers, true, dateColumns);
        int r = 2;
        for (List<String> row : rows) {
            writeRow(sb, r++, row, false, dateColumns);
        }
        sb.append("""
                  </sheetData>
                </worksheet>
                """);
        return sb.toString();
    }

    private static String colsXml(int count, List<Double> widths, java.util.Set<Integer> dateColumns) {
        StringBuilder sb = new StringBuilder("<cols>");
        for (int i = 0; i < count; i++) {
            double width = 22;
            if (widths != null && i < widths.size() && widths.get(i) != null) {
                width = widths.get(i);
            }
            int style = (dateColumns != null && dateColumns.contains(i)) ? 2 : 3;
            sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                    .append("\" width=\"").append(width)
                    .append("\" customWidth=\"1\" style=\"").append(style).append("\"/>");
        }
        sb.append("</cols>");
        return sb.toString();
    }

    private static void writeRow(
            StringBuilder sb,
            int rowNum,
            List<String> cells,
            boolean header,
            java.util.Set<Integer> dateColumns) {
        sb.append("<row r=\"").append(rowNum).append("\" ht=\"").append(header ? "22" : "18")
                .append("\" customHeight=\"1\">");
        for (int i = 0; i < cells.size(); i++) {
            String ref = colName(i) + rowNum;
            String value = cells.get(i) == null ? "" : cells.get(i);
            if (header) {
                sb.append("<c r=\"").append(ref).append("\" s=\"1\" t=\"inlineStr\"><is><t>")
                        .append(xml(value)).append("</t></is></c>");
            } else if (dateColumns != null && dateColumns.contains(i) && looksLikeIsoDate(value)) {
                sb.append("<c r=\"").append(ref).append("\" s=\"2\"><v>")
                        .append(excelSerial(value)).append("</v></c>");
            } else {
                sb.append("<c r=\"").append(ref).append("\" s=\"3\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(xml(value)).append("</t></is></c>");
            }
        }
        sb.append("</row>");
    }

    private static boolean looksLikeIsoDate(String value) {
        return value != null && value.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    private static long excelSerial(String isoDate) {
        java.time.LocalDate date = java.time.LocalDate.parse(isoDate);
        return java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.of(1899, 12, 30), date);
    }

    private static String colName(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        while (n >= 0) {
            sb.insert(0, (char) ('A' + (n % 26)));
            n = n / 26 - 1;
        }
        return sb.toString();
    }

    private static String xml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

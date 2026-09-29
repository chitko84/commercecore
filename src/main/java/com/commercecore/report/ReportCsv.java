package com.commercecore.report;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Renders report rows as RFC-4180 CSV. Every value is escaped (cells
 * containing commas, quotes or line breaks are quoted and embedded quotes
 * doubled), and user-controlled text cells starting with {@code = + - @}
 * (or a tab/CR) are prefixed with a single quote so spreadsheet formulas
 * cannot be injected. Numeric cells pass through unquoted and unprefixed.
 */
final class ReportCsv {
    private ReportCsv() { }

    static ResponseEntity<String> render(String filename, List<String> headers,
                                         List<List<Object>> rows) {
        StringBuilder csv = new StringBuilder();
        appendRow(csv, headers.stream().map(header -> (Object) header).toList());
        for (List<Object> row : rows) {
            appendRow(csv, row);
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        responseHeaders.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"");
        return ResponseEntity.ok().headers(responseHeaders).body(csv.toString());
    }

    private static void appendRow(StringBuilder csv, List<Object> cells) {
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) {
                csv.append(',');
            }
            csv.append(cell(cells.get(index)));
        }
        csv.append("\r\n");
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        String text = value.toString();
        String neutralized = neutralize(text);
        boolean needsQuotes = neutralized.indexOf(',') >= 0 || neutralized.indexOf('"') >= 0
                || neutralized.indexOf('\n') >= 0 || neutralized.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return neutralized;
        }
        return '"' + neutralized.replace("\"", "\"\"") + '"';
    }

    /**
     * Prefixes spreadsheet formula triggers so a crafted value cannot execute
     * when the CSV is opened in Excel/Sheets. Applied to every text cell so
     * no user-controlled field can smuggle an expression through.
     */
    private static String neutralize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }
}
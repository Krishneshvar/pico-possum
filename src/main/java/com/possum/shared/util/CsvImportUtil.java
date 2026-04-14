package com.possum.shared.util;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CsvImportUtil {

    private static final Pattern DECIMAL_PATTERN = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("[-+]?\\d+");

    private CsvImportUtil() {
    }

    public static List<List<String>> readCsv(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return parseCsv(content);
    }

    public static List<List<String>> parseCsv(String content) {
        List<List<String>> rows = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return rows;
        }

        List<String> currentRow = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);

            if (ch == '"') {
                if (inQuotes && i + 1 < content.length() && content.charAt(i + 1) == '"') {
                    currentField.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (!inQuotes && ch == ',') {
                currentRow.add(stripBom(currentField.toString()));
                currentField.setLength(0);
                continue;
            }

            if (!inQuotes && (ch == '\n' || ch == '\r')) {
                currentRow.add(stripBom(currentField.toString()));
                currentField.setLength(0);
                rows.add(currentRow);
                currentRow = new ArrayList<>();

                if (ch == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                continue;
            }

            currentField.append(ch);
        }

        if (currentField.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(stripBom(currentField.toString()));
            rows.add(currentRow);
        }

        return rows;
    }

    public static int findHeaderRowIndex(List<List<String>> rows, String... requiredColumns) {
        if (rows == null || rows.isEmpty() || requiredColumns == null || requiredColumns.length == 0) {
            return -1;
        }

        List<String> required = new ArrayList<>();
        for (String column : requiredColumns) {
            String normalized = normalizeHeader(column);
            if (!normalized.isEmpty()) {
                required.add(normalized);
            }
        }
        if (required.isEmpty()) {
            return -1;
        }

        for (int i = 0; i < rows.size(); i++) {
            Map<String, Integer> headerMap = buildHeaderIndex(rows.get(i));
            boolean matches = true;
            for (String column : required) {
                if (!headerMap.containsKey(column)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return i;
            }
        }

        return -1;
    }

    public static Map<String, Integer> buildHeaderIndex(List<String> headerRow) {
        Map<String, Integer> index = new HashMap<>();
        if (headerRow == null) {
            return index;
        }
        for (int i = 0; i < headerRow.size(); i++) {
            String normalized = normalizeHeader(headerRow.get(i));
            if (!normalized.isEmpty() && !index.containsKey(normalized)) {
                index.put(normalized, i);
            }
        }
        return index;
    }

    public static String getValue(List<String> row, Map<String, Integer> headerIndex, String... candidateHeaders) {
        if (row == null || headerIndex == null || candidateHeaders == null) {
            return "";
        }
        for (String header : candidateHeaders) {
            Integer idx = headerIndex.get(normalizeHeader(header));
            if (idx != null && idx >= 0 && idx < row.size()) {
                String value = row.get(idx);
                return value == null ? "" : value.trim();
            }
        }
        return "";
    }

    public static boolean isRowEmpty(List<String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        for (String value : row) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        String normalized = stripBom(value).trim().toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9]", "");
    }

    public static Integer parseInteger(String raw, Integer fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        Matcher matcher = INTEGER_PATTERN.matcher(raw.replace(",", ""));
        if (!matcher.find()) {
            return fallback;
        }
        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public static BigDecimal parseDecimal(String raw, BigDecimal fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        Matcher matcher = DECIMAL_PATTERN.matcher(raw.replace(",", ""));
        if (!matcher.find()) {
            return fallback;
        }
        try {
            return new BigDecimal(matcher.group());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String stripBom(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }
}

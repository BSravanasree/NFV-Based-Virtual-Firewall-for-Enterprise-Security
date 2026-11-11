package com.nexus.nfv.common;

import java.util.List;

/** RFC 4180 CSV writer with spreadsheet formula-injection protection (CWE-1236). */
public final class Csv {

    private Csv() {
    }

    public static String row(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(cell(values[i]));
        }
        return sb.append("\r\n").toString();
    }

    public static String document(List<String> header, List<Object[]> rows) {
        StringBuilder sb = new StringBuilder(row(header.toArray()));
        rows.forEach(r -> sb.append(row(r)));
        return sb.toString();
    }

    static String cell(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s; // neutralise formula interpretation
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}

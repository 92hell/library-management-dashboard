package com.helmifr.library.report;

import java.nio.charset.StandardCharsets;

/**
 * Builds RFC 4180 CSV (CRLF line ends, fields quoted when needed) that opens safely in a spreadsheet:
 * a cell starting with = + - @ or a tab/CR is prefixed with ' so it is shown as text, not run as a
 * formula (OWASP "CSV injection"). Starts with a UTF-8 byte order mark so Excel reads names correctly.
 */
final class Csv {

    private final StringBuilder out = new StringBuilder("﻿");

    Csv row(Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(cell(cells[i]));
        }
        out.append("\r\n");
        return this;
    }

    byte[] bytes() {
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        if (!(value instanceof Number) && !s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}

package com.scms.util;

public class InputSanitizer {

    // Trim, collapse multiple spaces, remove control chars (except newline) and limit length
    public static String sanitizeText(String input) {
        if (input == null) return null;
        // remove control characters except standard whitespace
        String cleaned = input.replaceAll("[\u0000-\u001F\u007F]+", "");
        // collapse multiple spaces
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        // enforce a reasonable maximum length
        if (cleaned.length() > 1024) return cleaned.substring(0, 1024);
        return cleaned;
    }

    // Allow multiline but limit length
    public static String sanitizeMultiline(String input, int maxLength) {
        if (input == null) return null;
        String cleaned = input.replaceAll("[\u0000-\u001F\u007F&&[^\\n\\r\\t]]+", "");
        if (cleaned.length() > maxLength) return cleaned.substring(0, maxLength);
        return cleaned;
    }

    // Parse doubles tolerant to comma or dot decimal separators, returns null on invalid input
    public static Double parseDoubleOrNull(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;
        // normalize comma decimal separators (e.g. "1,23" -> "1.23") and remove grouping spaces
        s = s.replaceAll("\\s", "");
        int comma = s.indexOf(',');
        int dot = s.indexOf('.');
        if (comma >= 0 && dot >= 0) {
            // ambiguous, remove grouping separators (commas before dot or dots before comma) - prefer last separator as decimal
            if (comma < dot) { // commas likely thousands separators
                s = s.replaceAll(",", "");
            } else { // dot as thousands separator
                s = s.replaceAll("\\.", "");
                s = s.replace(',', '.');
            }
        } else if (comma >= 0) {
            s = s.replace(',', '.');
        }

        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static double parseDoubleOrDefault(String input, double defaultValue) {
        Double d = parseDoubleOrNull(input);
        return d == null ? defaultValue : d;
    }
}

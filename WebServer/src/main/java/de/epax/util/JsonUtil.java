package de.epax.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonUtil {

    private JsonUtil() {}

    public static String escape(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static String string(String value) {
        return "\"" + escape(value) + "\"";
    }

    public static String object(Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(string(e.getKey())).append(":");
            sb.append(toJsonValue(e.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static String toJsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return string(s);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJsonValue(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((k, v) -> converted.put(String.valueOf(k), v));
            return object(converted);
        }
        return string(String.valueOf(value));
    }

    public static String extractString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int pos = json.indexOf(search);
        if (pos < 0) {
            search = "\"" + key + "\":";
            pos = json.indexOf(search);
            if (pos < 0) return null;
            pos += search.length();
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
            if (pos < json.length() && json.charAt(pos) == '"') {
                pos++;
                StringBuilder sb = new StringBuilder();
                while (pos < json.length()) {
                    char c = json.charAt(pos);
                    if (c == '\\' && pos + 1 < json.length()) {
                        char next = json.charAt(pos + 1);
                        switch (next) {
                            case '"': sb.append('"'); break;
                            case '\\': sb.append('\\'); break;
                            case 'n': sb.append('\n'); break;
                            case 'r': sb.append('\r'); break;
                            case 't': sb.append('\t'); break;
                            default: sb.append(next);
                        }
                        pos += 2;
                    } else if (c == '"') break;
                    else { sb.append(c); pos++; }
                }
                return sb.toString();
            }
            return null;
        }
        pos += search.length();
        StringBuilder sb = new StringBuilder();
        while (pos < json.length()) {
            char c = json.charAt(pos);
            if (c == '\\' && pos + 1 < json.length()) {
                char next = json.charAt(pos + 1);
                switch (next) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    default: sb.append(next);
                }
                pos += 2;
            } else if (c == '"') break;
            else { sb.append(c); pos++; }
        }
        return sb.toString();
    }

    public static long extractLong(String json, String key, long defaultVal) {
        String search = "\"" + key + "\":";
        int pos = json.indexOf(search);
        if (pos < 0) return defaultVal;
        pos += search.length();
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
        int end = pos;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        try {
            return Long.parseLong(json.substring(pos, end));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public static int extractInt(String json, String key, int defaultVal) {
        return (int) extractLong(json, key, defaultVal);
    }

    public static List<String> extractStringArray(String json, String key) {
        List<String> result = new ArrayList<>();
        String search = "\"" + key + "\":[";
        int pos = json.indexOf(search);
        if (pos < 0) return result;
        pos += search.length();
        int end = pos;
        int depth = 1;
        while (end < json.length() && depth > 0) {
            char c = json.charAt(end);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            if (depth > 0) end++;
        }
        String content = json.substring(pos, end).trim();
        int i = 0;
        while (i < content.length()) {
            if (content.charAt(i) == '"') {
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < content.length()) {
                    char c = content.charAt(i);
                    if (c == '\\' && i + 1 < content.length()) {
                        sb.append(content.charAt(i + 1));
                        i += 2;
                    } else if (c == '"') { i++; break; }
                    else { sb.append(c); i++; }
                }
                result.add(sb.toString());
            } else i++;
        }
        return result;
    }

    public static Map<String, String> parseSimpleObject(String body) {
        Map<String, String> map = new LinkedHashMap<>();
        if (body == null || body.isBlank()) return map;
        String trimmed = body.trim();
        if (!trimmed.startsWith("{")) return map;
        int i = 1;
        while (i < trimmed.length()) {
            while (i < trimmed.length() && (trimmed.charAt(i) == ' ' || trimmed.charAt(i) == ',')) i++;
            if (i >= trimmed.length() || trimmed.charAt(i) == '}') break;
            if (trimmed.charAt(i) != '"') { i++; continue; }
            i++;
            StringBuilder key = new StringBuilder();
            while (i < trimmed.length() && trimmed.charAt(i) != '"') key.append(trimmed.charAt(i++));
            i++;
            while (i < trimmed.length() && trimmed.charAt(i) != ':') i++;
            i++;
            while (i < trimmed.length() && trimmed.charAt(i) == ' ') i++;
            if (i >= trimmed.length()) break;
            String value;
            if (trimmed.charAt(i) == '"') {
                i++;
                StringBuilder val = new StringBuilder();
                while (i < trimmed.length()) {
                    char c = trimmed.charAt(i);
                    if (c == '\\' && i + 1 < trimmed.length()) { val.append(trimmed.charAt(i + 1)); i += 2; }
                    else if (c == '"') { i++; break; }
                    else { val.append(c); i++; }
                }
                value = val.toString();
            } else {
                StringBuilder val = new StringBuilder();
                while (i < trimmed.length() && trimmed.charAt(i) != ',' && trimmed.charAt(i) != '}') {
                    val.append(trimmed.charAt(i++));
                }
                value = val.toString().trim();
            }
            map.put(key.toString(), value);
        }
        return map;
    }
}

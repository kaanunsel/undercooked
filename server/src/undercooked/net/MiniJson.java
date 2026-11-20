package undercooked.net;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal zero-dependency JSON parser and serializer in plain Java.
 * Keeps the backend 100% framework-free per CMPE 436 constraints.
 */
public final class MiniJson {

    private MiniJson() {
    }

    // =========================================================================
    // Serialization helpers
    // =========================================================================

    public static String escape(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public static final class Obj {
        private final StringBuilder sb = new StringBuilder("{");
        private boolean first = true;

        public Obj put(String key, String val) {
            sep(key);
            sb.append(escape(val));
            return this;
        }

        public Obj put(String key, int val) {
            sep(key);
            sb.append(val);
            return this;
        }

        public Obj put(String key, long val) {
            sep(key);
            sb.append(val);
            return this;
        }

        public Obj put(String key, double val) {
            sep(key);
            // Round to 3 decimal places to keep 20Hz packets compact
            long scaled = Math.round(val * 1000.0);
            if (scaled % 1000 == 0) {
                sb.append(scaled / 1000);
            } else {
                sb.append(scaled / 1000.0);
            }
            return this;
        }

        public Obj put(String key, boolean val) {
            sep(key);
            sb.append(val);
            return this;
        }

        public Obj putRaw(String key, String rawJson) {
            sep(key);
            sb.append(rawJson == null ? "null" : rawJson);
            return this;
        }

        private void sep(String key) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(escape(key)).append(':');
        }

        @Override
        public String toString() {
            return sb + "}";
        }
    }

    public static final class Arr {
        private final StringBuilder sb = new StringBuilder("[");
        private boolean first = true;

        public Arr add(String val) {
            sep();
            sb.append(escape(val));
            return this;
        }

        public Arr addRaw(String rawJson) {
            sep();
            sb.append(rawJson == null ? "null" : rawJson);
            return this;
        }

        private void sep() {
            if (!first) {
                sb.append(',');
            }
            first = false;
        }

        @Override
        public String toString() {
            return sb + "]";
        }
    }

    // =========================================================================
    // Typed map readers
    // =========================================================================

    public static String getString(Map<String, Object> map, String key, String defaultVal) {
        Object v = map.get(key);
        return (v instanceof String s) ? s : defaultVal;
    }

    public static int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return defaultVal;
    }

    public static double getDouble(Map<String, Object> map, String key, double defaultVal) {
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return defaultVal;
    }

    public static boolean getBool(Map<String, Object> map, String key, boolean defaultVal) {
        Object v = map.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return defaultVal;
    }

    // =========================================================================
    // Recursive-descent JSON parser
    // =========================================================================

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        if (json == null) {
            throw new IllegalArgumentException("Null JSON input");
        }
        Parser p = new Parser(json);
        Object root = p.parseValue();
        p.skipWs();
        if (p.pos != p.src.length()) {
            throw new IllegalArgumentException("Trailing characters at index " + p.pos);
        }
        if (!(root instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Expected JSON object");
        }
        return (Map<String, Object>) root;
    }

    private static final class Parser {
        final String src;
        int pos = 0;

        Parser(String src) {
            this.src = src;
        }

        void skipWs() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWs();
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char c = src.charAt(pos);
            return switch (c) {
                case '{' -> parseObj();
                case '[' -> parseArr();
                case '"' -> parseStr();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield parseNum();
                    }
                    throw new IllegalArgumentException("Unexpected char '" + c + "' at " + pos);
                }
            };
        }

        Map<String, Object> parseObj() {
            pos++; // '{'
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                if (pos >= src.length() || src.charAt(pos) != '"') {
                    throw new IllegalArgumentException("Expected string key at " + pos);
                }
                String key = parseStr();
                skipWs();
                if (pos >= src.length() || src.charAt(pos) != ':') {
                    throw new IllegalArgumentException("Expected ':' at " + pos);
                }
                pos++;
                Object val = parseValue();
                map.put(key, val);
                skipWs();
                if (pos >= src.length()) {
                    throw new IllegalArgumentException("Unterminated object");
                }
                char next = src.charAt(pos);
                if (next == '}') {
                    pos++;
                    return map;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("Expected ',' or '}' at " + pos);
                }
                pos++;
            }
        }

        List<Object> parseArr() {
            pos++; // '['
            List<Object> list = new ArrayList<>();
            skipWs();
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (pos >= src.length()) {
                    throw new IllegalArgumentException("Unterminated array");
                }
                char next = src.charAt(pos);
                if (next == ']') {
                    pos++;
                    return list;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("Expected ',' or ']' at " + pos);
                }
                pos++;
            }
        }

        String parseStr() {
            pos++; // '"'
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= src.length()) {
                        throw new IllegalArgumentException("Unterminated escape");
                    }
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            if (pos + 4 > src.length()) {
                                throw new IllegalArgumentException("Invalid unicode escape");
                            }
                            int cp = Integer.parseInt(src.substring(pos, pos + 4), 16);
                            sb.append((char) cp);
                            pos += 4;
                        }
                        default -> throw new IllegalArgumentException("Invalid escape \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        Object parseLiteral(String lit, Object val) {
            if (src.startsWith(lit, pos)) {
                pos += lit.length();
                return val;
            }
            throw new IllegalArgumentException("Invalid literal at " + pos);
        }

        Number parseNum() {
            int start = pos;
            if (src.charAt(pos) == '-') {
                pos++;
            }
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
            boolean isFloat = false;
            if (pos < src.length() && src.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    pos++;
                }
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            String token = src.substring(start, pos);
            return isFloat ? Double.parseDouble(token) : Long.parseLong(token);
        }
    }
}

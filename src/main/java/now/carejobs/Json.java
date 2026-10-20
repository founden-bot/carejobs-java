package now.carejobs;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer, so the library needs nothing beyond the JDK.
 *
 * <p>Reading gives {@code Map<String, Object>} for objects (in the order the keys
 * came, read-only), {@code List<Object>} for arrays (read-only), {@code String},
 * {@code Boolean}, {@code null}, and for numbers a {@code Long} when the number is
 * a whole number that fits, else a {@code Double}.
 */
final class Json {

    private static final int MAX_DEPTH = 512;

    private Json() {
    }

    // -- reading -----------------------------------------------------------------------

    /**
     * Parses one JSON value.
     *
     * @throws IllegalArgumentException when the text is not exactly one JSON value
     */
    static Object parse(String text) {
        Reader reader = new Reader(text);
        reader.skipSpace();
        Object value = reader.value(0);
        reader.skipSpace();
        if (reader.pos != text.length()) {
            throw reader.error("unexpected text after the value");
        }
        return value;
    }

    private static final class Reader {
        private final String s;
        private int pos;

        Reader(String s) {
            this.s = s;
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Invalid JSON at character " + pos + ": " + message);
        }

        void skipSpace() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    return;
                }
            }
        }

        Object value(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("nested too deeply");
            }
            if (pos >= s.length()) {
                throw error("unexpected end");
            }
            char c = s.charAt(pos);
            switch (c) {
                case '{':
                    return object(depth);
                case '[':
                    return array(depth);
                case '"':
                    return string();
                case 't':
                    literal("true");
                    return Boolean.TRUE;
                case 'f':
                    literal("false");
                    return Boolean.FALSE;
                case 'n':
                    literal("null");
                    return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return number();
                    }
                    throw error("unexpected character '" + c + "'");
            }
        }

        void literal(String word) {
            if (!s.startsWith(word, pos)) {
                throw error("expected " + word);
            }
            pos += word.length();
        }

        Map<String, Object> object(int depth) {
            pos++; // {
            Map<String, Object> map = new LinkedHashMap<>();
            skipSpace();
            if (pos < s.length() && s.charAt(pos) == '}') {
                pos++;
                return Collections.unmodifiableMap(map);
            }
            while (true) {
                skipSpace();
                if (pos >= s.length() || s.charAt(pos) != '"') {
                    throw error("expected a key");
                }
                String key = string();
                skipSpace();
                if (pos >= s.length() || s.charAt(pos) != ':') {
                    throw error("expected ':'");
                }
                pos++;
                skipSpace();
                map.put(key, value(depth + 1));
                skipSpace();
                if (pos >= s.length()) {
                    throw error("unexpected end");
                }
                char c = s.charAt(pos++);
                if (c == '}') {
                    return Collections.unmodifiableMap(map);
                }
                if (c != ',') {
                    throw error("expected ',' or '}'");
                }
            }
        }

        List<Object> array(int depth) {
            pos++; // [
            List<Object> list = new ArrayList<>();
            skipSpace();
            if (pos < s.length() && s.charAt(pos) == ']') {
                pos++;
                return Collections.unmodifiableList(list);
            }
            while (true) {
                skipSpace();
                list.add(value(depth + 1));
                skipSpace();
                if (pos >= s.length()) {
                    throw error("unexpected end");
                }
                char c = s.charAt(pos++);
                if (c == ']') {
                    return Collections.unmodifiableList(list);
                }
                if (c != ',') {
                    throw error("expected ',' or ']'");
                }
            }
        }

        String string() {
            pos++; // opening quote
            StringBuilder out = new StringBuilder();
            while (true) {
                if (pos >= s.length()) {
                    throw error("unterminated string");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c < 0x20) {
                    throw error("control character in a string");
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (pos >= s.length()) {
                    throw error("unterminated escape");
                }
                char e = s.charAt(pos++);
                switch (e) {
                    case '"':
                        out.append('"');
                        break;
                    case '\\':
                        out.append('\\');
                        break;
                    case '/':
                        out.append('/');
                        break;
                    case 'b':
                        out.append('\b');
                        break;
                    case 'f':
                        out.append('\f');
                        break;
                    case 'n':
                        out.append('\n');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'u':
                        if (pos + 4 > s.length()) {
                            throw error("short \\u escape");
                        }
                        int code = 0;
                        for (int i = 0; i < 4; i++) {
                            int digit = Character.digit(s.charAt(pos + i), 16);
                            if (digit < 0) {
                                throw error("bad \\u escape");
                            }
                            code = code * 16 + digit;
                        }
                        pos += 4;
                        out.append((char) code);
                        break;
                    default:
                        throw error("bad escape \\" + e);
                }
            }
        }

        Object number() {
            int start = pos;
            if (s.charAt(pos) == '-') {
                pos++;
            }
            if (pos >= s.length()) {
                throw error("bad number");
            }
            if (s.charAt(pos) == '0') {
                pos++;
            } else if (s.charAt(pos) >= '1' && s.charAt(pos) <= '9') {
                while (pos < s.length() && Character.isDigit(s.charAt(pos)) && s.charAt(pos) < 128) {
                    pos++;
                }
            } else {
                throw error("bad number");
            }
            boolean whole = true;
            if (pos < s.length() && s.charAt(pos) == '.') {
                whole = false;
                pos++;
                int digits = digits();
                if (digits == 0) {
                    throw error("bad number");
                }
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                whole = false;
                pos++;
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                    pos++;
                }
                if (digits() == 0) {
                    throw error("bad number");
                }
            }
            String text = s.substring(start, pos);
            if (whole) {
                try {
                    return Long.parseLong(text);
                } catch (NumberFormatException tooBig) {
                    return Double.parseDouble(text);
                }
            }
            return Double.parseDouble(text);
        }

        private int digits() {
            int start = pos;
            while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') {
                pos++;
            }
            return pos - start;
        }
    }

    // -- writing -----------------------------------------------------------------------

    /**
     * Writes a value as compact JSON. Accepts maps with string keys, lists and
     * other iterables, arrays of objects, strings, numbers, booleans, null and
     * {@link Kind} (written as its slug).
     *
     * @throws IllegalArgumentException for anything else, or a number that is not finite
     */
    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, 0);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("nested too deeply");
        }
        if (value == null) {
            out.append("null");
        } else if (value instanceof String) {
            quote((String) value, out);
        } else if (value instanceof Kind) {
            quote(((Kind) value).value(), out);
        } else if (value instanceof Boolean) {
            out.append(value.toString());
        } else if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("JSON has no " + d);
            }
            if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                out.append((long) d);
            } else {
                out.append(d);
            }
        } else if (value instanceof BigDecimal) {
            out.append(((BigDecimal) value).toPlainString());
        } else if (value instanceof Number) {
            out.append(value.toString());
        } else if (value instanceof Map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new IllegalArgumentException("JSON object keys must be strings");
                }
                if (!first) {
                    out.append(',');
                }
                first = false;
                quote((String) entry.getKey(), out);
                out.append(':');
                write(entry.getValue(), out, depth + 1);
            }
            out.append('}');
        } else if (value instanceof Iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(item, out, depth + 1);
            }
            out.append(']');
        } else if (value instanceof Object[]) {
            out.append('[');
            Object[] items = (Object[]) value;
            for (int i = 0; i < items.length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(items[i], out, depth + 1);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("Cannot write a " + value.getClass().getName() + " as JSON");
        }
    }

    private static void quote(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                default:
                    if (c < 0x20 || c == ' ' || c == ' ') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }
}

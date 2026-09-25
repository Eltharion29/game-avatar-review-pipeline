package example.avatar;

import java.util.LinkedHashMap;
import java.util.Map;

final class Json {
    private Json() {}

    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c < 32 ? String.format("\\u%04x", (int) c) : c);
            }
        }
        return out.append('\"').toString();
    }

    static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.value();
        parser.space();
        if (parser.position != text.length()) throw new IllegalArgumentException("Trailing JSON content");
        return value;
    }

    private static final class Parser {
        private final String text;
        private int position;

        private Parser(String text) { this.text = text; }
        private void space() { while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++; }
        private char next() { return text.charAt(position++); }

        private Object value() {
            space();
            if (position >= text.length()) throw new IllegalArgumentException("Missing JSON value");
            return switch (text.charAt(position)) {
                case '{' -> object();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> result = new LinkedHashMap<>();
            next(); space();
            if (text.charAt(position) == '}') { position++; return result; }
            while (true) {
                String key = string(); space();
                if (next() != ':') throw new IllegalArgumentException("Expected colon");
                result.put(key, value()); space();
                char separator = next();
                if (separator == '}') return result;
                if (separator != ',') throw new IllegalArgumentException("Expected comma");
                space();
            }
        }

        private String string() {
            if (next() != '"') throw new IllegalArgumentException("Expected string");
            StringBuilder result = new StringBuilder();
            while (position < text.length()) {
                char c = next();
                if (c == '"') return result.toString();
                if (c != '\\') { result.append(c); continue; }
                char escaped = next();
                result.append(switch (escaped) {
                    case '"', '\\', '/' -> escaped;
                    case 'b' -> '\b'; case 'f' -> '\f'; case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                    case 'u' -> (char) Integer.parseInt(text.substring(position, position += 4), 16);
                    default -> throw new IllegalArgumentException("Invalid escape");
                });
            }
            throw new IllegalArgumentException("Unclosed string");
        }

        private Object number() {
            int start = position;
            while (position < text.length() && "-+.eE0123456789".indexOf(text.charAt(position)) >= 0) position++;
            String value = text.substring(start, position);
            return value.contains(".") || value.contains("e") || value.contains("E")
                    ? Double.parseDouble(value) : Long.parseLong(value);
        }

        private Object literal(String expected, Object value) {
            if (!text.startsWith(expected, position)) throw new IllegalArgumentException("Invalid literal");
            position += expected.length();
            return value;
        }
    }
}

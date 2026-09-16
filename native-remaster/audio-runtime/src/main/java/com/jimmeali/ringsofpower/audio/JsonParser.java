package com.jimmeali.ringsofpower.audio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonParser {
    private final String input;
    private int cursor;

    private JsonParser(String input) {
        this.input = input;
    }

    static Object parse(String input) {
        JsonParser parser = new JsonParser(input);
        Object value = parser.value();
        parser.whitespace();
        if (parser.cursor != input.length()) {
            throw parser.error("Trailing JSON content");
        }
        return value;
    }

    private Object value() {
        whitespace();
        if (cursor >= input.length()) {
            throw error("Expected JSON value");
        }
        char token = input.charAt(cursor);
        if (token == '{') {
            return object();
        }
        if (token == '[') {
            return array();
        }
        if (token == '"') {
            return string();
        }
        if (token == 't') {
            literal("true");
            return Boolean.TRUE;
        }
        if (token == 'f') {
            literal("false");
            return Boolean.FALSE;
        }
        if (token == 'n') {
            literal("null");
            return null;
        }
        if (token == '-' || Character.isDigit(token)) {
            return number();
        }
        throw error("Unexpected JSON token");
    }

    private Map<String, Object> object() {
        expect('{');
        Map<String, Object> result = new LinkedHashMap<>();
        whitespace();
        if (take('}')) {
            return result;
        }
        while (true) {
            whitespace();
            String key = string();
            whitespace();
            expect(':');
            if (result.containsKey(key)) {
                throw error("Duplicate object key: " + key);
            }
            result.put(key, value());
            whitespace();
            if (take('}')) {
                return result;
            }
            expect(',');
        }
    }

    private List<Object> array() {
        expect('[');
        List<Object> result = new ArrayList<>();
        whitespace();
        if (take(']')) {
            return result;
        }
        while (true) {
            result.add(value());
            whitespace();
            if (take(']')) {
                return result;
            }
            expect(',');
        }
    }

    private String string() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (cursor < input.length()) {
            char value = input.charAt(cursor++);
            if (value == '"') {
                return result.toString();
            }
            if (value == '\\') {
                if (cursor >= input.length()) {
                    throw error("Truncated string escape");
                }
                char escaped = input.charAt(cursor++);
                switch (escaped) {
                    case '"':
                    case '\\':
                    case '/':
                        result.append(escaped);
                        break;
                    case 'b':
                        result.append('\b');
                        break;
                    case 'f':
                        result.append('\f');
                        break;
                    case 'n':
                        result.append('\n');
                        break;
                    case 'r':
                        result.append('\r');
                        break;
                    case 't':
                        result.append('\t');
                        break;
                    case 'u':
                        result.append(unicode());
                        break;
                    default:
                        throw error("Invalid string escape");
                }
            } else {
                if (value < 0x20) {
                    throw error("Control character in string");
                }
                result.append(value);
            }
        }
        throw error("Unterminated string");
    }

    private char unicode() {
        if (cursor + 4 > input.length()) {
            throw error("Truncated Unicode escape");
        }
        try {
            int value = Integer.parseInt(input.substring(cursor, cursor + 4), 16);
            cursor += 4;
            return (char) value;
        } catch (NumberFormatException exception) {
            throw error("Invalid Unicode escape");
        }
    }

    private Number number() {
        int start = cursor;
        if (take('-')) {
            // sign consumed
        }
        digits();
        boolean decimal = false;
        if (take('.')) {
            decimal = true;
            digits();
        }
        if (take('e') || take('E')) {
            decimal = true;
            take('+');
            take('-');
            digits();
        }
        String value = input.substring(start, cursor);
        try {
            return decimal ? Double.valueOf(value) : Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw error("Invalid number");
        }
    }

    private void digits() {
        int start = cursor;
        while (cursor < input.length() && Character.isDigit(input.charAt(cursor))) {
            cursor++;
        }
        if (start == cursor) {
            throw error("Expected digit");
        }
    }

    private void literal(String expected) {
        if (!input.startsWith(expected, cursor)) {
            throw error("Invalid literal");
        }
        cursor += expected.length();
    }

    private void whitespace() {
        while (cursor < input.length() && Character.isWhitespace(input.charAt(cursor))) {
            cursor++;
        }
    }

    private boolean take(char expected) {
        if (cursor < input.length() && input.charAt(cursor) == expected) {
            cursor++;
            return true;
        }
        return false;
    }

    private void expect(char expected) {
        if (!take(expected)) {
            throw error("Expected '" + expected + "'");
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at character " + cursor);
    }
}

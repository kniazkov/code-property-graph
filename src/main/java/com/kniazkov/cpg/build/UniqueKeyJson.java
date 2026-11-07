/* JSON configuration input must not silently overwrite target facts. MIT License. */
package com.kniazkov.cpg.build;
import com.kniazkov.json.*;
import java.util.HashSet;

/** Strict JSON parsing plus a structural duplicate-key check before map conversion. */
final class UniqueKeyJson {
    private UniqueKeyJson() {}
    static Object parse(String source) throws JsonException {
        JsonElement document = Json.parse(source, JsonParsingMode.STRICT);
        // The JSON library validates syntax but its object map is last-key-wins.
        // Walk the already validated lexical structure, decoding keys through the
        // same library so escaped-equivalent names cannot bypass uniqueness.
        new Keys(source).value();
        return document.toJavaObject();
    }
    private static final class Keys {
        final String source; int cursor;
        Keys(String source) { this.source = source; }
        void space() { while (cursor < source.length() && (Character.isWhitespace(source.charAt(cursor)) || cursor == 0 && source.charAt(cursor) == '\ufeff')) cursor++; }
        String string() throws JsonException {
            int start = cursor++;
            while (source.charAt(cursor) != '"') {
                if (source.charAt(cursor) == '\\') cursor++;
                cursor++;
            }
            cursor++;
            return (String) Json.parse(source.substring(start, cursor), JsonParsingMode.STRICT).toJavaObject();
        }
        void value() throws JsonException {
            space(); char token = source.charAt(cursor);
            if (token == '{') {
                cursor++; space(); var names = new HashSet<String>();
                if (source.charAt(cursor) == '}') { cursor++; return; }
                while (true) {
                    space(); int offset = cursor; String key = string();
                    if (!names.add(key)) throw new IllegalArgumentException("Duplicate JSON key '" + key + "' at offset " + offset);
                    space(); cursor++; value(); space();
                    if (source.charAt(cursor++) == '}') return;
                }
            } else if (token == '[') {
                cursor++; space(); if (source.charAt(cursor) == ']') { cursor++; return; }
                while (true) { value(); space(); if (source.charAt(cursor++) == ']') return; }
            } else if (token == '"') string();
            else while (cursor < source.length() && ",]} \t\r\n".indexOf(source.charAt(cursor)) < 0) cursor++;
        }
    }
}

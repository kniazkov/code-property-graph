/* Small lossless Doxygen tag scanner; raw spelling remains on Comment. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;
import java.util.regex.*;

final class DoxygenParser {
    private DoxygenParser() {}
    private static final Pattern COMMAND = Pattern.compile("(?<![\\w])[@\\\\]([A-Za-z]+)\\b");
    private static final Pattern NAME = Pattern.compile("\\s*(?:\\[[^]\\r\\n]*\\]\\s*)?([A-Za-z_][A-Za-z_0-9]*)");
    static List<NodeBuilder<? extends DocumentationEntry>> parse(String text, SourceRange range) {
        if (!(text.startsWith("/**") || text.startsWith("/*!") || text.startsWith("///") || text.startsWith("//!"))) return List.of();
        var result = new ArrayList<NodeBuilder<? extends DocumentationEntry>>();
        var starts = new ArrayList<Integer>(); var ends = new ArrayList<Integer>(); var commands = new ArrayList<String>();
        Matcher matcher = COMMAND.matcher(text);
        while (matcher.find()) { starts.add(matcher.start()); ends.add(matcher.end()); commands.add(matcher.group(1)); }
        for (int i = 0; i < starts.size(); i++) {
            DocumentationEntry.Kind kind = switch (commands.get(i)) {
                case "brief" -> DocumentationEntry.Kind.BRIEF;
                case "param" -> DocumentationEntry.Kind.PARAM;
                case "tparam" -> DocumentationEntry.Kind.TPARAM;
                case "return", "returns" -> DocumentationEntry.Kind.RETURN;
                default -> null;
            };
            if (kind == null) continue;
            int end = i + 1 < starts.size() ? starts.get(i + 1) : text.endsWith("*/") ? text.length() - 2 : text.length();
            int description = ends.get(i);
            var builder = DocumentationEntry.create().setKind(kind);
            SourceRange tag = span(text, range, starts.get(i), end);
            if (tag != null) { builder.setTagRange(tag); builder.setRange(tag); }
            if (kind == DocumentationEntry.Kind.PARAM || kind == DocumentationEntry.Kind.TPARAM) {
                Matcher name = NAME.matcher(withoutDecoration(text)); name.region(description, end);
                if (name.lookingAt()) {
                    builder.setName(name.group(1)); description = name.end();
                    SourceRange nameRange = span(text, range, name.start(1), name.end(1));
                    if (nameRange != null) { builder.setNameRange(nameRange); builder.setRange(nameRange); }
                }
            }
            builder.setDescription(text.substring(description, end).replaceAll("(?m)^[ \\t]*\\*[ \\t]?", "").strip());
            result.add(builder);
        }
        return result;
    }
    /** Mask rather than remove block-line decoration so name spans remain physical. */
    private static String withoutDecoration(String text) {
        var logical = new StringBuilder(text);
        Matcher decoration = Pattern.compile("(?m)^[ \\t]*\\*(?!/)").matcher(text);
        while (decoration.find()) logical.setCharAt(decoration.end() - 1, ' ');
        return logical.toString();
    }
    private static SourceRange span(String text, SourceRange base, int from, int to) {
        return base == null ? null : new SourceRange(base.file(), position(text, base.start(), from), position(text, base.start(), to));
    }
    private static SourcePosition position(String text, SourcePosition base, int offset) {
        int line = base.line(), column = base.column();
        for (int i = 0; i < offset; i++) {
            char ch = text.charAt(i);
            if (ch == '\r') { line++; column = 1; }
            else if (ch == '\n') { if (i == 0 || text.charAt(i - 1) != '\r') line++; column = 1; }
            else column++;
        }
        return new SourcePosition(line, column, base.offset() + offset);
    }
}

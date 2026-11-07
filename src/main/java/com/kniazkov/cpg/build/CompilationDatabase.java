package com.kniazkov.cpg.build;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import com.kniazkov.json.Json;
import com.kniazkov.cpg.build.Build.*;

/** Reads compilation commands without invoking a shell, compiler, or response file. */
public final class CompilationDatabase {
    private CompilationDatabase() {}
    public static Build read(Path database) throws IOException {
        Path base = database.toAbsolutePath().normalize().getParent();
        List<?> entries;
        try {
            Object document = UniqueKeyJson.parse(Files.readString(database));
            if (!(document instanceof List<?> list)) throw new IllegalArgumentException(database + ": expected an array of compilation entries");
            entries = list;
        }
        catch (com.kniazkov.json.JsonException | ClassCastException e) { throw new IllegalArgumentException(database + ": invalid compilation database", e); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException(database + ": " + e.getMessage(), e); }
        List<TranslationUnit> units = new ArrayList<>();
        for (int n = 0; n < entries.size(); n++) {
            String name = database + ": entry " + n;
            try {
                if (!(entries.get(n) instanceof Map<?,?> entry)) throw new IllegalArgumentException("expected an object");
                String file = string(entry.get("file"), "file");
                name += " (" + file + ")";
                Path directory = base.resolve(string(entry.get("directory"), "directory")).normalize();
                Path source = directory.resolve(file).normalize();
                List<String> args = new ArrayList<>();
                if (entry.containsKey("arguments")) {
                    if (!(entry.get("arguments") instanceof List<?> array)) throw new IllegalArgumentException("arguments must be an array of strings");
                    for (int i = 0; i < array.size(); i++) args.add(string(array.get(i), "arguments[" + i + "]"));
                } else args.addAll(tokenize(string(entry.get("command"), "command")));
                if (args.isEmpty()) throw new IllegalArgumentException("empty command");
                List<IncludePath> includes = new ArrayList<>();
                List<MacroOperation> macros = new ArrayList<>();
                String standard = null;
                for (int i = 1; i < args.size(); i++) {
                    int argumentIndex = i;
                    String arg = args.get(i);
                    String key = null, value = null;
                    for (String candidate : List.of("-isystem", "-iquote", "-I", "-D", "-U", "-std")) {
                        if (arg.equals(candidate)) {
                            key = candidate;
                            if (++i >= args.size()) throw new IllegalArgumentException("missing operand for " + key);
                            value = args.get(i); break;
                        }
                        if (arg.startsWith(candidate) && !candidate.equals("-std")) {
                            key = candidate; value = arg.substring(candidate.length()); break;
                        }
                        if (arg.startsWith("-std=")) { key = "-std"; value = arg.substring(5); break; }
                    }
                    if (key != null) {
                        if (key.equals("-I") && value.equals("-")) throw new IllegalArgumentException("unsupported compiler option -I-");
                        if (value.isEmpty()) throw new IllegalArgumentException("empty operand for " + key);
                        switch (key) {
                            case "-std" -> standard = value;
                            case "-D", "-U" -> macros.add(new MacroOperation(key.equals("-D"), value,
                                    "argument:" + argumentIndex, arg, args.subList(argumentIndex, i + 1)));
                            default -> includes.add(new IncludePath(key, directory.resolve(value).normalize()));
                        }
                    } else if (Set.of("-o", "-MF", "-MT", "-MQ").contains(arg)) {
                        if (++i >= args.size()) throw new IllegalArgumentException("missing operand for " + arg);
                    } else if (Set.of("-c", "-g", "-Wall", "-Wextra", "-pedantic", "-pedantic-errors", "-MMD", "-MD", "-MP").contains(arg)
                            || arg.matches("-O[0-3sg]") || arg.startsWith("-o") && arg.length() > 2) {
                        // Output, dependency and diagnostic flags do not alter this parser's input.
                    } else if (!arg.startsWith("-") && directory.resolve(arg).normalize().equals(source)) {
                        // Source operand.
                    } else throw new IllegalArgumentException("unsupported compiler option " + arg);
                }
                PlatformProfile platform = PlatformProfile.defaults();
                if (entry.containsKey("platformProfile")) {
                    if (!(entry.get("platformProfile") instanceof Map<?, ?> settings))
                        throw new IllegalArgumentException("platformProfile must be an object");
                    platform = PlatformProfile.fromSettings(settings);
                }
                units.add(new TranslationUnit(new PhysicalSource(source), directory, includes, macros, standard,
                        ConfigurationSource.database(database, n, args), platform));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException(name + ": " + ex.getMessage(), ex);
            }
        }
        return new Build(units);
    }

    private static String string(Object value, String field) {
        if (!(value instanceof String text) || text.isEmpty())
            throw new IllegalArgumentException(field + " must be a nonempty string");
        return text;
    }

    /** POSIX quoting subset, with no expansion. Shell control/expansion syntax is rejected. */
    public static List<String> tokenize(String command) {
        List<String> result = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        char quote = 0;
        boolean started = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '\\' && quote != '\'') {
                if (++i == command.length()) throw new IllegalArgumentException("trailing command escape");
                char next = command.charAt(i);
                if (next == '\n') continue;
                if (next == '\r') throw new IllegalArgumentException("CR/CRLF command continuation is unsupported; use LF");
                if (quote == '"' && next != '"' && next != '\\' && next != '$' && next != '`') word.append('\\');
                word.append(next); started = true;
            } else if (quote != 0) {
                if (c == quote) quote = 0;
                else {
                    if (quote == '"' && (c == '$' || c == '`')) throw new IllegalArgumentException("shell expansion is unsupported");
                    word.append(c);
                }
            } else if (c == '\'' || c == '"') { quote = c; started = true; }
            else if (c == '\n' || c == '\r') throw new IllegalArgumentException("shell command newline is unsupported");
            else if (Character.isWhitespace(c)) {
                if (started) { result.add(word.toString()); word.setLength(0); started = false; }
            } else {
                if ("|&;<>()$`\n\r".indexOf(c) >= 0) throw new IllegalArgumentException("shell syntax is unsupported");
                word.append(c); started = true;
            }
        }
        if (quote != 0) throw new IllegalArgumentException("unterminated command quote");
        if (started) result.add(word.toString());
        return List.copyOf(result);
    }
}

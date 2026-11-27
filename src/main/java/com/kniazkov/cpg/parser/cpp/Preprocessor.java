package com.kniazkov.cpg.parser.cpp;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Applies the first two translation phases to a source string.
 *
 * <p>The normalizer performs trigraph replacement (2.3) and line splicing
 * (2.1-003) in the order required by the translation phases. The result can be
 * passed to {@link Lexer#tokenize(String)}.</p>
 */
public final class Preprocessor {

    private static final Map<Character, Character> TRIGRAPHS = Map.ofEntries(
            Map.entry('=', '#'),
            Map.entry('/', '\\'),
            Map.entry('\'', '^'),
            Map.entry('(', '['),
            Map.entry(')', ']'),
            Map.entry('!', '|'),
            Map.entry('<', '{'),
            Map.entry('>', '}'),
            Map.entry('-', '~'));

    private final Lexer lexer = new Lexer();

    /**
     * Replaces trigraphs and splices backslash-new-line sequences.
     *
     * @param source the source text
     * @return the normalized text
     * @throws NullPointerException if {@code source} is {@code null}
     */
    public String normalize(String source) {
        Objects.requireNonNull(source, "source");
        return splice(replaceTrigraphs(source));
    }

    /**
     * Normalizes the source and tokenizes it.
     *
     * @param source the source text
     * @return the preprocessing tokens
     */
    public List<Token> preprocess(String source) {
        return lexer.tokenize(normalize(source));
    }

    private static String replaceTrigraphs(String source) {
        StringBuilder result = new StringBuilder(source.length());
        int index = 0;
        while (index < source.length()) {
            char ch = source.charAt(index);
            if (ch == '?' && index + 2 < source.length()
                    && source.charAt(index + 1) == '?') {
                Character replacement = TRIGRAPHS.get(source.charAt(index + 2));
                if (replacement != null) {
                    result.append(replacement);
                    index += 3;
                    continue;
                }
            }
            result.append(ch);
            index++;
        }
        return result.toString();
    }

    private static String splice(String source) {
        StringBuilder result = new StringBuilder(source.length());
        int index = 0;
        while (index < source.length()) {
            char ch = source.charAt(index);
            if (ch == '\\' && index + 1 < source.length()) {
                char next = source.charAt(index + 1);
                if (next == '\n') {
                    index += 2;
                    continue;
                }
                if (next == '\r') {
                    index += 2;
                    if (index < source.length() && source.charAt(index) == '\n') {
                        index++;
                    }
                    continue;
                }
            }
            result.append(ch);
            index++;
        }
        return result.toString();
    }
}

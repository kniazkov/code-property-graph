/* Code Property Graph: C++03 translation phases 1–3. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.SourcePosition;
import com.kniazkov.cpg.model.SourceRange;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static com.kniazkov.cpg.parser.PreprocessingToken.Kind.*;

/**
 * C++03 lexical front end, independent of the deliberately smaller parser grammar.
 * Input is decoded Unicode in a Java String; offsets and columns use UTF-16 code
 * units. CRLF and CR become one logical newline. Trigraph replacement precedes
 * one-pass line splicing, then maximal-munch tokenization and comment replacement.
 * This API performs no macro expansion, literal evaluation or type checking.
 */
public final class CppLexer {
    private CppLexer() {}

    /** Immutable phase-3 text, tokens, physical character mappings and physical EOF. */
    public record Result(String text, List<SourceRange> characterRanges,
            List<PreprocessingToken> tokens, SourcePosition eof) {
        public Result {
            characterRanges = List.copyOf(characterRanges);
            tokens = List.copyOf(tokens);
        }
    }

    private static final Map<String, String> ALTERNATIVES = Map.ofEntries(
        Map.entry("<%", "{"), Map.entry("%>", "}"), Map.entry("<:", "["),
        Map.entry(":>", "]"), Map.entry("%:", "#"), Map.entry("%:%:", "##"),
        Map.entry("and", "&&"), Map.entry("and_eq", "&="), Map.entry("bitand", "&"),
        Map.entry("bitor", "|"), Map.entry("compl", "~"), Map.entry("not", "!"),
        Map.entry("not_eq", "!="), Map.entry("or", "||"), Map.entry("or_eq", "|="),
        Map.entry("xor", "^"), Map.entry("xor_eq", "^="));
    // Longest first. C++03 has neither the modern <:: exception nor >> splitting.
    private static final String[] OPERATORS = {
        "%:%:", ">>=", "<<=", "->*", "...", "##", "::", ".*", "->", "++", "--",
        "<<", ">>", "<=", ">=", "==", "!=", "&&", "||", "*=", "/=", "%=", "+=", "-=",
        "&=", "^=", "|=", "<%", "%>", "<:", ":>", "%:",
        "{", "}", "[", "]", "#", "(", ")", ";", ":", "?", ".", "+", "-", "*", "/",
        "%", "^", "&", "|", "~", "!", "=", "<", ">", ","
    };

    /** Scans one physical file; malformed partial tokens report physical locations. */
    public static Result scan(String file, String source) {
        return new Scanner(file, source).scan();
    }

    private static final class Scanner {
        private final String file;
        private final String input;
        private final List<SourceRange> inputRanges;
        private final SourcePosition eof;
        private final StringBuilder output = new StringBuilder();
        private final List<SourceRange> outputRanges = new ArrayList<>();
        private final List<PreprocessingToken> tokens = new ArrayList<>();
        private int offset;
        // 0: first token on line, 1: after #, 2: after # include, 3: ordinary text.
        private int directive;

        Scanner(String file, String source) {
            this.file = file;
            var phaseOne = new StringBuilder();
            var ranges = new ArrayList<SourceRange>();
            int line = 1, column = 1;
            for (int i = 0; i < source.length();) {
                var start = new SourcePosition(line, column, i);
                char ch = source.charAt(i++);
                if (ch == '\r' || ch == '\n') {
                    if (ch == '\r' && i < source.length() && source.charAt(i) == '\n') i++;
                    ch = '\n'; line++; column = 1;
                } else {
                    if (ch == '?' && i + 1 < source.length() && source.charAt(i) == '?') {
                        int tri = "=/'()!<>-".indexOf(source.charAt(i + 1));
                        if (tri >= 0) { ch = "#\\^[]|{}~".charAt(tri); i += 2; column += 2; }
                    }
                    column++;
                }
                phaseOne.append(ch);
                ranges.add(new SourceRange(file, start, new SourcePosition(line, column, i)));
            }
            eof = new SourcePosition(line, column, source.length());
            var phaseTwo = new StringBuilder();
            inputRanges = new ArrayList<>();
            for (int i = 0; i < phaseOne.length(); i++) {
                if (phaseOne.charAt(i) == '\\' && i + 1 < phaseOne.length() && phaseOne.charAt(i + 1) == '\n') {
                    i++; // Do not rescan the newly adjacent characters.
                } else {
                    phaseTwo.append(phaseOne.charAt(i)); inputRanges.add(ranges.get(i));
                }
            }
            input = phaseTwo.toString();
        }

        Result scan() {
            while (offset < input.length()) {
                int start = offset;
                char ch = input.charAt(offset);
                if (ch == '\n') {
                    offset++; emit(NEWLINE, start, offset, null); directive = 0; continue;
                }
                if (space(ch)) {
                    while (offset < input.length() && space(input.charAt(offset))) offset++;
                    emit(WHITESPACE, start, offset, null); continue;
                }
                if (input.startsWith("//", offset) || input.startsWith("/*", offset)) {
                    boolean block = input.charAt(offset + 1) == '*';
                    offset += 2;
                    if (block) {
                        while (offset < input.length() && !input.startsWith("*/", offset)) offset++;
                        if (offset == input.length()) throw error(start, "Unterminated comment");
                        offset += 2;
                    } else while (offset < input.length() && input.charAt(offset) != '\n') offset++;
                    int logical = output.length();
                    SourceRange range = range(start, offset);
                    output.append(' '); outputRanges.add(range);
                    tokens.add(new PreprocessingToken(WHITESPACE, " ", " ", range, logical, logical + 1));
                    continue;
                }
                PreprocessingToken.Kind kind;
                String canonical = null;
                if (directive == 2 && (ch == '<' || ch == '"')) {
                    char close = ch == '<' ? '>' : '"';
                    offset++;
                    while (offset < input.length() && input.charAt(offset) != close && input.charAt(offset) != '\n') offset++;
                    if (offset == input.length() || input.charAt(offset) != close) throw error(start, "Unterminated header name");
                    if (offset == start + 1) throw error(start, "Empty header name");
                    offset++; kind = HEADER_NAME;
                } else if (ch == '\'' || ch == '"' || (ch == 'L' && offset + 1 < input.length()
                        && (input.charAt(offset + 1) == '\'' || input.charAt(offset + 1) == '"'))) {
                    if (ch == 'L') offset++;
                    char quote = input.charAt(offset++);
                    while (offset < input.length() && input.charAt(offset) != quote && input.charAt(offset) != '\n') {
                        if (input.charAt(offset) == '\\') {
                            if (ucnStart(offset)) offset += ucnLength(offset, false);
                            else { offset++; if (offset < input.length() && input.charAt(offset) != '\n') offset++; }
                        } else offset++;
                    }
                    if (offset == input.length() || input.charAt(offset) != quote)
                        throw error(start, quote == '"' ? "Unterminated string literal" : "Unterminated character literal");
                    if (quote == '\'' && offset == start + (ch == 'L' ? 2 : 1)) throw error(start, "Empty character literal");
                    offset++; kind = quote == '"' ? STRING_LITERAL : CHARACTER_LITERAL;
                } else if (digit(ch) || (ch == '.' && offset + 1 < input.length() && digit(input.charAt(offset + 1)))) {
                    offset++;
                    while (offset < input.length()) {
                        char next = input.charAt(offset);
                        if ((next == '+' || next == '-') && (input.charAt(offset - 1) == 'e' || input.charAt(offset - 1) == 'E')) offset++;
                        else if (digit(next) || next == '.') offset++;
                        else { int size = nondigit(offset, false); if (size == 0) break; offset += size; }
                    }
                    kind = PP_NUMBER;
                } else if (nondigit(offset) > 0) {
                    var name = new StringBuilder();
                    do {
                        int size = nondigit(offset);
                        if (size > 0) {
                            if (ucnStart(offset)) name.appendCodePoint(ucnValue(offset, size));
                            else name.append(input, offset, offset + size);
                            offset += size;
                        } else if (digit(input.charAt(offset))) name.append(input.charAt(offset++));
                        else break;
                    } while (offset < input.length());
                    canonical = name.toString();
                    kind = ALTERNATIVES.containsKey(canonical) || canonical.equals("new") || canonical.equals("delete") ? OPERATOR : IDENTIFIER;
                    canonical = ALTERNATIVES.getOrDefault(canonical, canonical);
                } else {
                    String operator = null;
                    for (String candidate : OPERATORS) if (input.startsWith(candidate, offset)) { operator = candidate; break; }
                    if (operator == null) { offset++; kind = OTHER; }
                    else { offset += operator.length(); kind = OPERATOR; canonical = ALTERNATIVES.getOrDefault(operator, operator); }
                }
                emit(kind, start, offset, canonical);
                PreprocessingToken token = tokens.get(tokens.size() - 1);
                if (directive == 0 && token.canonicalSpelling().equals("#")) directive = 1;
                else if (directive == 1 && token.spelling().equals("include")) directive = 2;
                else directive = 3;
            }
            tokens.add(new PreprocessingToken(EOF, "", "", new SourceRange(file, eof, eof), output.length(), output.length()));
            return new Result(output.toString(), outputRanges, tokens, eof);
        }

        private int nondigit(int at) { return nondigit(at, true); }
        private int nondigit(int at, boolean identifier) {
            char ch = input.charAt(at);
            if (ch == '_' || ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z') return 1;
            if (ucnStart(at)) return ucnLength(at, identifier);
            if (ch >= 128 && (CppIdentifierCharacters.contains(input.codePointAt(at))
                    || !identifier && input.codePointAt(at) >= 0xa0
                    && !(input.codePointAt(at) >= 0xd800 && input.codePointAt(at) <= 0xdfff))) return Character.charCount(input.codePointAt(at));
            return 0;
        }
        private boolean ucnStart(int at) {
            return input.charAt(at) == '\\' && at + 1 < input.length() && (input.charAt(at + 1) == 'u' || input.charAt(at + 1) == 'U');
        }
        private int ucnLength(int at, boolean identifier) {
            int length = input.charAt(at + 1) == 'u' ? 6 : 10;
            if (at + length > input.length()) throw error(at, "Incomplete universal character name");
            for (int i = at + 2; i < at + length; i++) if (Character.digit(input.charAt(i), 16) < 0 || input.charAt(i) > 127)
                throw error(at, "Invalid universal character name");
            long value = Long.parseLong(input.substring(at + 2, at + length), 16);
            if (value > 0x10ffff || value >= 0xd800 && value <= 0xdfff || value < 0xa0 && value != '$' && value != '@' && value != '`')
                throw error(at, "Invalid universal character name");
            if (identifier && !CppIdentifierCharacters.contains((int) value)) throw error(at, "Universal character name is not permitted in a C++03 identifier");
            return length;
        }
        private int ucnValue(int at, int length) { return (int) Long.parseLong(input.substring(at + 2, at + length), 16); }
        private void emit(PreprocessingToken.Kind kind, int start, int end, String canonical) {
            int logical = output.length();
            String spelling = input.substring(start, end);
            output.append(spelling); outputRanges.addAll(inputRanges.subList(start, end));
            tokens.add(new PreprocessingToken(kind, spelling, canonical == null ? spelling : canonical, range(start, end), logical, output.length()));
        }
        private SourceRange range(int start, int end) { return new SourceRange(file, inputRanges.get(start).start(), inputRanges.get(end - 1).end()); }
        private ParseException error(int at, String reason) {
            SourcePosition pos = inputRanges.get(at).start();
            return new ParseException(file, pos.line(), pos.column(), reason);
        }
    }
    private static boolean digit(char ch) { return ch >= '0' && ch <= '9'; }
    private static boolean space(char ch) { return ch == ' ' || ch == '\t' || ch == '\u000b' || ch == '\f'; }

    /** Identifier check for canonical spellings passed to the smaller parser. */
    static boolean isIdentifier(String text) {
        if (text.isEmpty()) return false;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            if (!(cp == '_' || cp >= 'a' && cp <= 'z' || cp >= 'A' && cp <= 'Z'
                    || CppIdentifierCharacters.contains(cp) || i > 0 && cp >= '0' && cp <= '9')) return false;
            i += Character.charCount(cp);
        }
        return true;
    }

    /** Compatibility adapter for the current parser; directives remain one token until phase 4. */
    static List<Token> parserTokens(String file, String source) {
        Result result = scan(file, source);
        var converted = new ArrayList<Token>();
        boolean lineStart = true;
        for (int i = 0; i < result.tokens().size(); i++) {
            PreprocessingToken token = result.tokens().get(i);
            if (token.kind() == WHITESPACE) continue;
            if (token.kind() == NEWLINE) { lineStart = true; continue; }
            if (token.kind() == EOF) { converted.add(new Token("<eof>", result.eof(), result.eof())); break; }
            if (token.kind() == OTHER) throw new ParseException(file, token.range().start().line(),
                    token.range().start().column(), "Unsupported character: " + token.spelling());
            if (lineStart && token.canonicalSpelling().equals("#")) {
                var directive = new StringBuilder("#");
                SourcePosition end = token.range().end();
                while (i + 1 < result.tokens().size()) {
                    PreprocessingToken next = result.tokens().get(i + 1);
                    if (next.kind() == NEWLINE || next.kind() == EOF) break;
                    i++; directive.append(next.spelling()); end = next.range().end();
                }
                converted.add(new Token(directive.toString(), token.range().start(), end));
            } else converted.add(new Token(token.canonicalSpelling(), token.range().start(), token.range().end()));
            lineStart = false;
        }
        return List.copyOf(converted);
    }
}

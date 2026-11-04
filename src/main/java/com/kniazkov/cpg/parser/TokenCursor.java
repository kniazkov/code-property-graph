/* Code Property Graph: shared parser position and source diagnostics. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.*;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Shares one position among the declaration, statement and expression readers of one file. */
final class TokenCursor {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Set<String> RESERVED = Set.of("class", "int", "void", "return", "if", "else", "while", "for", "public", "static");
    private final String file;
    private final Language language;
    private final List<Token> tokens;
    private int position;
    private final Preprocessing preprocessing;

    TokenCursor(String file, List<Token> tokens, Language language) {
        this(file, tokens, language, null);
    }

    TokenCursor(String file, List<Token> tokens, Language language, Preprocessing preprocessing) {
        this.preprocessing = preprocessing;
        this.language = language;
        this.file = file;
        this.tokens = tokens;
    }

    record Mark(SourceRange range, int tokenIndex) {}

    private SourceRange range(Token token) {
        return new SourceRange(token.file() == null ? file : token.file(), token.start(), token.end());
    }

    Mark start() { return new Mark(range(tokens.get(position)), position); }
    SourcePosition position() { return tokens.get(position).start(); }
    SourceRange currentRange() { return range(tokens.get(position)); }

    /** Finds a semicolon without crossing a brace or an unbalanced delimiter. Never loops at EOF. */
    SourceRange recoverInitializer(Mark start) {
        int parentheses = 0, brackets = 0;
        for (int index = start.tokenIndex(); index < tokens.size(); index++) {
            if (tokens.get(index).problem() != null) return null;
            String text = tokens.get(index).text();
            if (text.equals("<eof>") || text.equals("{") || text.equals("}")) return null;
            if (text.equals("(")) parentheses++;
            else if (text.equals(")") && --parentheses < 0) return null;
            else if (text.equals("[")) brackets++;
            else if (text.equals("]") && --brackets < 0) return null;
            else if (text.equals(";")) {
                if (parentheses != 0 || brackets != 0) return null;
                var end = range(tokens.get(index));
                if (!start.range().file().equals(end.file()) || end.start().offset() < start.range().start().offset()) return null;
                position = index;
                return new SourceRange(end.file(), start.range().start(), end.start());
            }
        }
        return null;
    }

    /** Attaches the interval from the first token to the end of the last consumed token. */
    <B extends NodeBuilder<?>> B span(Mark start, B builder) {
        SourceRange first = start.range();
        if (position <= start.tokenIndex()) {
            builder.setRange(new SourceRange(first.file(), first.start(), first.start()));
            if (preprocessing != null) {
                int begin = tokens.get(start.tokenIndex()).sourceIndex();
                builder.setProvenance(new TokenProvenance(preprocessing, begin, begin));
            }
            return builder;
        }
        SourceRange last = range(tokens.get(position - 1));
        // A syntax node can span included sources. Its primary range must never
        // invent a physical interval across files; full origins are separate.
        if (first.file().equals(last.file()) && last.end().offset() >= first.start().offset())
            builder.setRange(new SourceRange(first.file(), first.start(), last.end()));
        else builder.setRange(first);
        if (preprocessing != null) {
            int begin = tokens.get(start.tokenIndex()).sourceIndex();
            int end = tokens.get(position - 1).sourceIndex() + 1;
            builder.setProvenance(new TokenProvenance(preprocessing, begin, end));
        }
        return builder;
    }

    /** Reads the next spelling without consuming it. */
    String peek() {
        Token token = tokens.get(position);
        if (token.problem() != null) throw token.problem();
        return token.text();
    }

    /** Unsupported declarations still report their strict-edition violation, not just the leading keyword. */
    void checkUnsupportedDeclaration() {
        int braces = 0;
        for (int index = position; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            if (token.problem() != null) throw token.problem();
            if (token.text().equals("{")) braces++;
            if (token.text().equals("}")) { if (braces == 0) return; braces--; }
            if (braces == 0 && token.text().equals(";") || token.text().equals("<eof>")) return;
        }
    }

    /** Consumes a token already recognized by its caller. */
    void advance() { if (position < tokens.size() - 1) position++; }

    /** Consumes an optional token only if it matches. */
    boolean take(String spelling) {
        if (!peek().equals(spelling)) return false;
        advance();
        return true;
    }

    /** Consumes a required delimiter or keyword, reporting its expected and actual spelling. */
    void expect(String spelling) {
        if (!take(spelling)) throw error("Expected '" + spelling + "', found '" + peek() + "'");
    }

    /** Consumes an identifier permitted by the supported grammar. */
    String identifier() {
        String name = peek();
        if (!(language == Language.CPP ? CppLexer.isIdentifier(name) : IDENTIFIER.matcher(name).matches()) || RESERVED.contains(name)) throw error("Expected identifier");
        advance();
        return name;
    }

    /** Attaches the current token position to a grammar diagnostic. */
    ParseException error(String reason) {
        Token token = tokens.get(position);
        return new ParseException(java.nio.file.Path.of(token.file() == null ? file : token.file()).getFileName().toString(), token.start().line(), token.start().column(), reason, range(token));
    }
}

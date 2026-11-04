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

    TokenCursor(String file, List<Token> tokens, Language language) {
        this.language = language;
        this.file = file;
        this.tokens = tokens;
    }

    SourcePosition start() { return tokens.get(position).start(); }

    /** Attaches the interval from the first token to the end of the last consumed token. */
    <B extends NodeBuilder<?>> B span(SourcePosition start, B builder) {
        builder.setRange(new SourceRange(file, start, tokens.get(position - 1).end()));
        return builder;
    }

    /** Reads the next spelling without consuming it. */
    String peek() { return tokens.get(position).text(); }

    /** Consumes a token already recognized by its caller. */
    void advance() { position++; }

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
        return new ParseException(java.nio.file.Path.of(file).getFileName().toString(), token.start().line(), token.start().column(), reason);
    }
}

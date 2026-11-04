/* Code Property Graph: shared parser position and source diagnostics. MIT License. */
package com.kniazkov.cpg.parser;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Shares one position among the declaration, statement and expression readers of one file. */
final class TokenCursor {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Set<String> RESERVED = Set.of("class", "int", "void", "return", "if", "while", "for", "public", "static");
    private final String file;
    private final List<Token> tokens;
    private int position;

    TokenCursor(String file, List<Token> tokens) {
        this.file = file;
        this.tokens = tokens;
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
        if (!IDENTIFIER.matcher(name).matches() || RESERVED.contains(name)) throw error("Expected identifier");
        advance();
        return name;
    }

    /** Attaches the current token position to a grammar diagnostic. */
    ParseException error(String reason) {
        Token token = tokens.get(position);
        return new ParseException(file, token.line(), token.column(), reason);
    }
}

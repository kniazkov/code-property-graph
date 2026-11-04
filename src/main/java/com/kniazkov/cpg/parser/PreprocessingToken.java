/* Code Property Graph: C++03 preprocessing token. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.SourceRange;

/**
 * A phase-3 token, before macro expansion or conversion to a language token.
 * Spelling retains digraphs and universal character names; canonicalSpelling
 * decodes identifier UCNs and maps alternative operators to their primary forms.
 * Logical offsets are half-open UTF-16 indices into CppLexer.Result.text().
 * The physical range includes intervening deleted line splices, but not a splice
 * after the last character. Whitespace and newlines are explicit for phase 4.
 */
public record PreprocessingToken(Kind kind, String spelling, String canonicalSpelling,
        SourceRange range, int logicalStart, int logicalEnd) {
    public enum Kind {
        IDENTIFIER, PP_NUMBER, CHARACTER_LITERAL, STRING_LITERAL, HEADER_NAME,
        OPERATOR, OTHER, WHITESPACE, NEWLINE, EOF
    }
}

/* Code Property Graph: positioned lexical token. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.SourcePosition;

/** Parser spelling and primary physical location, optionally supplied by preprocessing. */
record Token(String text, SourcePosition start, SourcePosition end, String file, int sourceIndex) {
    Token(String text, SourcePosition start, SourcePosition end) { this(text, start, end, null, -1); }
    Token(String text, SourcePosition start, SourcePosition end, String file) { this(text, start, end, file, -1); }
}

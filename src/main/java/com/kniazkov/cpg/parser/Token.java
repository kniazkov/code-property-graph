/* Code Property Graph: positioned lexical token. MIT License. */
package com.kniazkov.cpg.parser;

/** Source spelling and one-based location of the first character of a token. */
record Token(String text, com.kniazkov.cpg.model.SourcePosition start, com.kniazkov.cpg.model.SourcePosition end) {}

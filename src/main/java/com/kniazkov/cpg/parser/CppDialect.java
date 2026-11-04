/* Code Property Graph: strict C++03 checks on active expanded tokens. MIT License. */
package com.kniazkov.cpg.parser;

import java.util.List;
import java.util.Set;

/** Checks phase-4 output so inactive conditional text and unused macros are not rejected. */
final class CppDialect {
    private static final Set<String> EXTENSIONS = Set.of("__attribute__", "__attribute", "__declspec",
            "__asm__", "__asm", "__typeof__", "__typeof", "__extension__", "__auto_type",
            "__inline__", "__inline", "__restrict__", "__restrict", "__thread", "__int128", "__float128",
            "__alignof__", "__alignof", "__complex__", "__real__", "__imag__", "__label__",
            "__const", "__const__", "__volatile", "__volatile__", "__signed", "__signed__",
            "__cdecl", "__stdcall", "__fastcall", "__thiscall", "__vectorcall", "__forceinline", "__int64",
            "_Complex", "_Imaginary");
    private static final Set<String> MODERN_PREFIXES = Set.of("u8", "u", "U", "R", "u8R", "uR", "UR", "LR");
    private CppDialect() {}

    static String violation(List<CppPreprocessor.ExpandedToken> tokens, int index) {
        var expanded = tokens.get(index);
        var token = expanded.token();
        String text = token.canonicalSpelling();
        if (EXTENSIONS.contains(text) || text.startsWith("__builtin_"))
            return "Compiler extension '" + text + "' is not permitted in C++03";
        if (token.kind() == PreprocessingToken.Kind.OTHER) return "Unsupported character: " + token.spelling();
        if (token.kind() == PreprocessingToken.Kind.PP_NUMBER) {
            String lower = text.toLowerCase(java.util.Locale.ROOT);
            if (lower.startsWith("0b") || lower.matches(".*[0-9a-f]ll[u]?") || lower.matches(".*[0-9a-f]ull")
                    || lower.startsWith("0x") && lower.contains("p") || text.contains("_") || text.contains("'"))
                return "Literal '" + text + "' is not permitted in C++03";
        }
        if (index + 1 < tokens.size()) {
            var next = tokens.get(index + 1).token();
            boolean adjacent = token.range().file().equals(next.range().file()) && token.logicalEnd() == next.logicalStart();
            if (adjacent && MODERN_PREFIXES.contains(text)
                    && (next.kind() == PreprocessingToken.Kind.STRING_LITERAL || next.kind() == PreprocessingToken.Kind.CHARACTER_LITERAL))
                return "Literal prefix '" + text + "' is not permitted in C++03";
            if (adjacent && (token.kind() == PreprocessingToken.Kind.STRING_LITERAL || token.kind() == PreprocessingToken.Kind.CHARACTER_LITERAL)
                    && next.kind() == PreprocessingToken.Kind.IDENTIFIER)
                return "User-defined literal is not permitted in C++03";
            if (text.equals("[") && next.canonicalSpelling().equals("["))
                return "Attribute syntax '[[' is not permitted in C++03";
        }
        return null;
    }
}

/* C++03 fundamental type identity, independent of ABI widths. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** The distinct C++03 fundamental identities. Synonymous spellings have the same kind. */
public enum FundamentalKind {
    VOID("void"), BOOL("bool"), CHAR("char"), SIGNED_CHAR("signed char"), UNSIGNED_CHAR("unsigned char"),
    WCHAR_T("wchar_t"), SHORT("short"), UNSIGNED_SHORT("unsigned short"), INT("int"), UNSIGNED_INT("unsigned int"),
    LONG("long"), UNSIGNED_LONG("unsigned long"), FLOAT("float"), DOUBLE("double"), LONG_DOUBLE("long double");
    private final String spelling;
    FundamentalKind(String spelling) { this.spelling = spelling; }
    public String spelling() { return spelling; }
    /** Classifies valid permutations only; repeated or incompatible specifiers are rejected. */
    public static Optional<FundamentalKind> fromSpelling(String spelling) {
        var words = Arrays.asList(spelling.trim().split("\\s+"));
        var set = new HashSet<>(words);
        if (set.size() != words.size()) return Optional.empty();
        if (set.contains("signed") && set.contains("unsigned")) return Optional.empty();
        boolean unsigned = set.remove("unsigned"), signed = set.remove("signed");
        if (set.isEmpty()) return Optional.of(unsigned ? UNSIGNED_INT : INT);
        if (set.equals(Set.of("char"))) return Optional.of(unsigned ? UNSIGNED_CHAR : signed ? SIGNED_CHAR : CHAR);
        if (set.contains("short") || set.contains("long")) {
            boolean longer = set.contains("long");
            if (longer && !unsigned && !signed && set.equals(Set.of("long", "double"))) return Optional.of(LONG_DOUBLE);
            if (set.equals(Set.of(longer ? "long" : "short")) || set.equals(Set.of(longer ? "long" : "short", "int")))
                return Optional.of(longer ? unsigned ? UNSIGNED_LONG : LONG : unsigned ? UNSIGNED_SHORT : SHORT);
            return Optional.empty();
        }
        if (set.equals(Set.of("int"))) return Optional.of(unsigned ? UNSIGNED_INT : INT);
        if (unsigned || signed || set.size() != 1) return Optional.empty();
        return Arrays.stream(values()).filter(kind -> kind.spelling.equals(words.getFirst())).findFirst();
    }
}

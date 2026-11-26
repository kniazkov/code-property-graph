package com.kniazkov.cpg.tree.cpp;

/**
 * A C++03 type that can be produced by a literal.
 */
public enum CppType {

    CHAR("char"),
    WCHAR_T("wchar_t"),
    INT("int"),
    UNSIGNED_INT("unsigned int"),
    LONG_INT("long int"),
    UNSIGNED_LONG_INT("unsigned long int"),
    BOOL("bool");

    private final String displayName;

    CppType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the spelling used in C++ source and diagnostics.
     *
     * @return the display name
     */
    public String displayName() {
        return displayName;
    }
}

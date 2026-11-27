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
    BOOL("bool"),
    FLOAT("float"),
    DOUBLE("double"),
    LONG_DOUBLE("long double"),
    CHAR_ARRAY("array of const char"),
    WCHAR_T_ARRAY("array of const wchar_t");

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

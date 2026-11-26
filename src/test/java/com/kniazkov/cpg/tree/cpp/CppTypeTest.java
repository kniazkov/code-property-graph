package com.kniazkov.cpg.tree.cpp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CppTypeTest {

    @Test
    void exposesCppSpellings() {
        assertEquals("char", CppType.CHAR.displayName());
        assertEquals("wchar_t", CppType.WCHAR_T.displayName());
        assertEquals("int", CppType.INT.displayName());
        assertEquals("unsigned int", CppType.UNSIGNED_INT.displayName());
        assertEquals("long int", CppType.LONG_INT.displayName());
        assertEquals("unsigned long int", CppType.UNSIGNED_LONG_INT.displayName());
        assertEquals("bool", CppType.BOOL.displayName());
    }
}

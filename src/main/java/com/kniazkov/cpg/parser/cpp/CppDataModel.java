package com.kniazkov.cpg.parser.cpp;

/**
 * Widths of the integer types used to resolve the type of an integer literal.
 *
 * @param intBits bit width of {@code int}
 * @param longBits bit width of {@code long int}
 */
public record CppDataModel(int intBits, int longBits) {

    /** A 32-bit {@code int} and a 32-bit {@code long int}. */
    public static final CppDataModel ILP32 = new CppDataModel(32, 32);

    /** A 32-bit {@code int} and a 64-bit {@code long int}. */
    public static final CppDataModel LP64 = new CppDataModel(32, 64);
}

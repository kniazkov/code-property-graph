package com.kniazkov.cpg.codegen;

/**
 * Common language identifiers used by {@link CodeGeneratorRegistry}.
 *
 * <p>Identifiers are plain strings so that new languages can be added without
 * changing the registry or the generator interface.</p>
 */
public final class Languages {

    /** C++03 language identifier. */
    public static final String CPP03 = "cpp03";

    /** C language identifier. */
    public static final String C = "c";

    /** Java language identifier. */
    public static final String JAVA = "java";

    private Languages() { }
}

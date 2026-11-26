package com.kniazkov.cpg.tree.cpp;

import com.kniazkov.cpg.tree.TypedLiteral;

/**
 * A C++ literal whose type is one of the C++03 literal types.
 */
public interface CppTypedLiteral extends TypedLiteral {

    /**
     * Returns the resolved C++ type.
     *
     * @return the type
     */
    CppType type();

    @Override
    default String typeName() {
        return type().displayName();
    }
}

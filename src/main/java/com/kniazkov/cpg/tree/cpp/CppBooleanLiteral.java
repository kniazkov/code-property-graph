package com.kniazkov.cpg.tree.cpp;

import com.kniazkov.cpg.tree.BooleanLiteral;
import com.kniazkov.cpg.tree.Fragment;
import com.kniazkov.cpg.tree.Node;
import com.kniazkov.cpg.tree.Visitor;

import java.util.List;
import java.util.Objects;

/**
 * A C++03 boolean literal parsed according to 2.13.5.
 */
public final class CppBooleanLiteral implements BooleanLiteral, CppTypedLiteral {

    private final Fragment fragment;
    private final boolean value;

    /**
     * Creates a boolean literal node.
     *
     * @param fragment the source fragment
     * @param value the boolean value
     */
    public CppBooleanLiteral(Fragment fragment, boolean value) {
        this.fragment = Objects.requireNonNull(fragment, "fragment");
        this.value = value;
    }

    @Override
    public Fragment fragment() {
        return fragment;
    }

    @Override
    public String data() {
        return value ? "true" : "false";
    }

    @Override
    public String name() {
        return "BooleanLiteral";
    }

    @Override
    public List<Node> children() {
        return List.of();
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean value() {
        return value;
    }

    @Override
    public CppType type() {
        return CppType.BOOL;
    }

    /**
     * Returns whether a boolean literal is an lvalue.
     *
     * @return always {@code false}
     */
    public boolean isLvalue() {
        return false;
    }
}

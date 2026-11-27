package com.kniazkov.cpg.tree.cpp;

import com.kniazkov.cpg.tree.Fragment;
import com.kniazkov.cpg.tree.Node;
import com.kniazkov.cpg.tree.StringLiteral;
import com.kniazkov.cpg.tree.Visitor;

import java.util.List;
import java.util.Objects;

/**
 * A C++03 string literal parsed according to 2.13.4.
 */
public final class CppStringLiteral implements StringLiteral, CppTypedLiteral {

    private final Fragment fragment;
    private final String data;
    private final boolean wide;
    private final List<Integer> chars;
    private final CppType type;

    /**
     * Creates a string literal node.
     *
     * @param fragment the source fragment
     * @param data the literal text
     * @param wide whether the literal has the {@code L} prefix
     * @param chars the character codes, including the terminating null
     * @param type the resolved array type
     */
    public CppStringLiteral(
            Fragment fragment,
            String data,
            boolean wide,
            List<Integer> chars,
            CppType type) {
        this.fragment = Objects.requireNonNull(fragment, "fragment");
        this.data = Objects.requireNonNull(data, "data");
        this.wide = wide;
        this.chars = List.copyOf(chars);
        this.type = Objects.requireNonNull(type, "type");
    }

    @Override
    public Fragment fragment() {
        return fragment;
    }

    @Override
    public String data() {
        return data;
    }

    @Override
    public String name() {
        return "StringLiteral";
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
    public boolean wide() {
        return wide;
    }

    @Override
    public List<Integer> chars() {
        return chars;
    }

    @Override
    public CppType type() {
        return type;
    }
}

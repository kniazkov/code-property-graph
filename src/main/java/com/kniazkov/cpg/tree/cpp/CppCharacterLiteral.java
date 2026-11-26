package com.kniazkov.cpg.tree.cpp;

import com.kniazkov.cpg.tree.CharacterLiteral;
import com.kniazkov.cpg.tree.Fragment;
import com.kniazkov.cpg.tree.Node;
import com.kniazkov.cpg.tree.Visitor;

import java.util.List;
import java.util.Objects;

/**
 * A C++03 character literal parsed according to 2.13.2.
 */
public final class CppCharacterLiteral implements CharacterLiteral, CppTypedLiteral {

    private final Fragment fragment;
    private final String data;
    private final boolean wide;
    private final List<Integer> cchars;
    private final CppType type;
    private final int value;

    /**
     * Creates a character literal node.
     *
     * @param fragment the source fragment
     * @param data the literal text
     * @param wide whether the literal has the {@code L} prefix
     * @param cchars the character codes
     * @param type the resolved type
     * @param value the literal value
     */
    public CppCharacterLiteral(
            Fragment fragment,
            String data,
            boolean wide,
            List<Integer> cchars,
            CppType type,
            int value) {
        this.fragment = Objects.requireNonNull(fragment, "fragment");
        this.data = Objects.requireNonNull(data, "data");
        this.wide = wide;
        this.cchars = List.copyOf(cchars);
        this.type = Objects.requireNonNull(type, "type");
        this.value = value;
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
        return "CharacterLiteral";
    }

    @Override
    public List<Node> children() {
        return List.of();
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visit(this);
    }

    /**
     * Returns whether the literal has the {@code L} prefix.
     *
     * @return {@code true} for a wide-character literal
     */
    public boolean wide() {
        return wide;
    }

    /**
     * Returns the character codes.
     *
     * @return an immutable list of codes
     */
    public List<Integer> cchars() {
        return cchars;
    }

    @Override
    public int value() {
        return value;
    }

    @Override
    public CppType type() {
        return type;
    }
}

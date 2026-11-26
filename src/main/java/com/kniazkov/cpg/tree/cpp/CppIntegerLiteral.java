package com.kniazkov.cpg.tree.cpp;

import com.kniazkov.cpg.tree.Fragment;
import com.kniazkov.cpg.tree.IntegerLiteral;
import com.kniazkov.cpg.tree.Node;
import com.kniazkov.cpg.tree.Visitor;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/**
 * A C++03 integer literal parsed according to 2.13.1.
 */
public final class CppIntegerLiteral implements IntegerLiteral, CppTypedLiteral {

    private final Fragment fragment;
    private final String data;
    private final BigInteger value;
    private final int radix;
    private final String suffix;
    private final CppType type;

    /**
     * Creates an integer literal node.
     *
     * @param fragment the source fragment
     * @param data the literal text
     * @param value the numeric value
     * @param radix the base
     * @param suffix the normalized suffix
     * @param type the resolved type
     */
    public CppIntegerLiteral(
            Fragment fragment,
            String data,
            BigInteger value,
            int radix,
            String suffix,
            CppType type) {
        this.fragment = Objects.requireNonNull(fragment, "fragment");
        this.data = Objects.requireNonNull(data, "data");
        this.value = Objects.requireNonNull(value, "value");
        this.radix = radix;
        this.suffix = Objects.requireNonNull(suffix, "suffix");
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
        return "IntegerLiteral";
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
    public BigInteger value() {
        return value;
    }

    @Override
    public int radix() {
        return radix;
    }

    @Override
    public String suffix() {
        return suffix;
    }

    @Override
    public CppType type() {
        return type;
    }
}

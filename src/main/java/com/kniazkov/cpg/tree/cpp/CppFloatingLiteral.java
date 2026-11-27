package com.kniazkov.cpg.tree.cpp;

import com.kniazkov.cpg.tree.FloatingLiteral;
import com.kniazkov.cpg.tree.Fragment;
import com.kniazkov.cpg.tree.Node;
import com.kniazkov.cpg.tree.Visitor;

import java.util.List;
import java.util.Objects;

/**
 * A C++03 floating literal parsed according to 2.13.3.
 */
public final class CppFloatingLiteral implements FloatingLiteral, CppTypedLiteral {

    private final Fragment fragment;
    private final String data;
    private final double value;
    private final String suffix;
    private final CppType type;

    /**
     * Creates a floating literal node.
     *
     * @param fragment the source fragment
     * @param data the literal text
     * @param value the literal value
     * @param suffix the normalized suffix
     * @param type the resolved type
     */
    public CppFloatingLiteral(
            Fragment fragment,
            String data,
            double value,
            String suffix,
            CppType type) {
        this.fragment = Objects.requireNonNull(fragment, "fragment");
        this.data = Objects.requireNonNull(data, "data");
        this.value = value;
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
        return "FloatingLiteral";
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
    public double value() {
        return value;
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

package com.kniazkov.cpg.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestNode implements Node {

    private final Fragment fragment;
    private final List<Node> children;
    private final List<String> tags;
    private final String name;
    private final String data;

    public TestNode(
            Fragment fragment,
            List<Node> children,
            List<String> tags) {
        this(fragment, children, tags, null, null);
    }

    public TestNode(
            Fragment fragment,
            List<Node> children,
            List<String> tags,
            String name,
            String data) {
        this.fragment = fragment;
        this.children = children == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(children));
        this.tags = tags == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(tags));
        this.name = name;
        this.data = data;
    }

    public static TestNode leaf(Fragment fragment) {
        return new TestNode(fragment, List.of(), List.of());
    }

    public static TestNode of(Fragment fragment, List<Node> children, List<String> tags) {
        return new TestNode(fragment, children, tags);
    }

    @Override
    public Fragment fragment() {
        return fragment;
    }

    @Override
    public List<Node> children() {
        return children;
    }

    @Override
    public List<String> tags() {
        return tags;
    }

    @Override
    public String name() {
        return name == null ? Node.super.name() : name;
    }

    @Override
    public String data() {
        return data == null ? Node.super.data() : data;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visit(this);
    }
}

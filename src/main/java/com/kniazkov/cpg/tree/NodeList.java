package com.kniazkov.cpg.tree;

import java.util.List;
import java.util.Objects;

/**
 * A structural node that groups a homogeneous sequence of child nodes.
 *
 * <p>A list node is itself a child of another node and carries that parent's
 * tag. Its own {@link #tags()} is empty; the elements are accessed through
 * {@link #elements()} and are not tagged by the list.</p>
 *
 * @param <E> the element type
 */
public abstract class NodeList<E extends Node> implements Node {

    private final Fragment fragment;
    private final List<E> elements;

    /**
     * Creates a list node.
     *
     * @param fragment the source fragment covered by the list
     * @param elements the list elements
     * @throws NullPointerException if any argument is {@code null} or an
     *         element is {@code null}
     */
    protected NodeList(Fragment fragment, List<E> elements) {
        this.fragment = Objects.requireNonNull(fragment, "fragment");
        this.elements = List.copyOf(elements);
    }

    /**
     * Returns the list elements.
     *
     * @return an immutable list of elements, never {@code null}
     */
    public List<E> elements() {
        return elements;
    }

    @Override
    public Fragment fragment() {
        return fragment;
    }

    @Override
    public List<Node> children() {
        return List.copyOf(elements);
    }

    @Override
    public List<String> tags() {
        return List.of();
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visit(this);
    }
}

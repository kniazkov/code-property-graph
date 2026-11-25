package com.kniazkov.cpg.tree;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A node in the C++ syntax tree.
 *
 * <p>Implementations are expected to be immutable. Child nodes are returned in
 * source order. A node may have no children, in which case it is a leaf.</p>
 *
 * <p>Tags are unique within a node. A regular node has exactly one tag per
 * child. A {@link NodeList} has no tags; its children are list elements. A
 * list-valued child is represented by a single tagged {@link NodeList} node,
 * not by several children with the same tag.</p>
 */
public interface Node {

    /**
     * Returns the source fragment covered by this node.
     *
     * @return the source fragment, never {@code null}
     */
    Fragment fragment();

    /**
     * Returns the node type name.
     *
     * <p>The default implementation returns the simple Java class name, or the
     * fully qualified name for an anonymous class. Concrete nodes may override
     * this method to provide a stable language-level name.</p>
     *
     * @return the node type name, never {@code null}
     */
    default String name() {
        String simpleName = getClass().getSimpleName();
        return simpleName.isEmpty() ? getClass().getName() : simpleName;
    }

    /**
     * Returns the node data as text.
     *
     * <p>Most nodes have no data and return an empty string. Leaf nodes such as
     * literals may override this method to expose their value in a textual
     * form.</p>
     *
     * @return the node data, never {@code null}
     */
    default String data() {
        return "";
    }

    /**
     * Returns the child nodes in source order.
     *
     * <p>The returned list must not be modified by callers.</p>
     *
     * @return an immutable list of children, never {@code null}
     */
    List<Node> children();

    /**
     * Returns the outgoing non-AST references of this node.
     *
     * <p>AST child edges are exposed by {@link #children()}. This method adds
     * the remaining code property graph edges, such as control flow, data
     * flow, calls, declarations, scopes, and semantic relations.</p>
     *
     * <p>The returned list must not be modified by callers.</p>
     *
     * @return an immutable list of references, never {@code null}
     */
    default List<Reference> references() {
        return List.of();
    }

    /**
     * Returns whether this node has no children.
     *
     * @return {@code true} if this node is a leaf
     */
    default boolean isLeaf() {
        return children().isEmpty();
    }

    /**
     * Returns the child at the given index, if it exists.
     *
     * @param index the zero-based child index
     * @return the child at {@code index}, or an empty optional if the index is
     *         out of range
     */
    default Optional<Node> child(int index) {
        if (index < 0 || index >= children().size()) {
            return Optional.empty();
        }
        return Optional.of(children().get(index));
    }

    /**
     * Returns the child associated with the given tag, if it exists.
     *
     * <p>The default implementation resolves the tag through {@link #tags()}.
     * Tags are unique, so the result is unambiguous.</p>
     *
     * @param tag the child tag
     * @return the tagged child, or an empty optional if the tag is unknown
     * @throws NullPointerException if {@code tag} is {@code null}
     */
    default Optional<Node> child(String tag) {
        Objects.requireNonNull(tag, "tag");
        int index = tags().indexOf(tag);
        if (index < 0 || index >= children().size()) {
            return Optional.empty();
        }
        return Optional.of(children().get(index));
    }

    /**
     * Returns the tags of this node's children.
     *
     * <p>Tags are unique within a node. A regular node returns one tag per
     * child, in the same order as {@link #children()}. A {@link NodeList}
     * returns an empty list.</p>
     *
     * @return an immutable list of child tags, never {@code null}
     */
    default List<String> tags() {
        return List.of();
    }

    /**
     * Dispatches this node to the given visitor.
     *
     * @param visitor the visitor to dispatch to
     * @param <R> the result type of the visitor
     * @return the result produced by the visitor
     */
    <R> R accept(Visitor<R> visitor);
}

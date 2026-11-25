package com.kniazkov.cpg.graph;

import com.kniazkov.cpg.tree.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An immutable collection of directed links of a single relation type.
 *
 * <p>A relation maps a source node to zero or more target nodes. Every link in
 * one instance belongs to the same relation type, for example declarations or
 * calls. Different relation types are kept in separate instances, so a
 * structure never mixes unrelated edges.</p>
 *
 * <p>Lookup uses node identity, not {@link Object#equals(Object)}. The
 * instance is immutable: it can be shared and queried from several threads
 * without synchronization.</p>
 */
public final class Relations {

    private static final Relations EMPTY = new Relations(Map.of());

    private final Map<Node, List<Node>> outgoing;

    private Relations(Map<Node, List<Node>> outgoing) {
        this.outgoing = outgoing;
    }

    /**
     * Returns an empty relation.
     *
     * @return a relation without links
     */
    public static Relations empty() {
        return EMPTY;
    }

    /**
     * Creates a builder for a relation.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns all targets linked from the given source node.
     *
     * @param source the source node
     * @return an immutable list of targets, empty if there are none
     * @throws NullPointerException if {@code source} is {@code null}
     */
    public List<Node> targets(Node source) {
        Objects.requireNonNull(source, "source");
        List<Node> targets = outgoing.get(source);
        return targets == null ? List.of() : targets;
    }

    /**
     * Returns the first target linked from the given source node.
     *
     * @param source the source node
     * @return the first target, or an empty optional if there are none
     * @throws NullPointerException if {@code source} is {@code null}
     */
    public Optional<Node> target(Node source) {
        List<Node> targets = targets(source);
        return targets.isEmpty() ? Optional.empty() : Optional.of(targets.get(0));
    }

    /**
     * Returns whether the given source node has at least one target.
     *
     * @param source the source node
     * @return {@code true} if the source has a target
     * @throws NullPointerException if {@code source} is {@code null}
     */
    public boolean contains(Node source) {
        Objects.requireNonNull(source, "source");
        return outgoing.containsKey(source);
    }

    /**
     * Returns all source nodes that have at least one target.
     *
     * @return an immutable set of source nodes
     */
    public Set<Node> sources() {
        return outgoing.keySet();
    }

    /**
     * Returns whether this relation has no links.
     *
     * @return {@code true} if there are no links
     */
    public boolean isEmpty() {
        return outgoing.isEmpty();
    }

    /**
     * Builds an immutable {@link Relations} instance.
     */
    public static final class Builder {

        private final Map<Node, List<Node>> outgoing = new IdentityHashMap<>();

        private Builder() { }

        /**
         * Adds a link from the source node to the target node.
         *
         * @param source the source node
         * @param target the target node
         * @return this builder
         * @throws NullPointerException if {@code source} or {@code target} is
         *         {@code null}
         */
        public Builder add(Node source, Node target) {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            outgoing.computeIfAbsent(source, key -> new ArrayList<>()).add(target);
            return this;
        }

        /**
         * Builds the relation and freezes its content.
         *
         * @return the immutable relation
         */
        public Relations build() {
            if (outgoing.isEmpty()) {
                return EMPTY;
            }
            Map<Node, List<Node>> copy = new IdentityHashMap<>();
            for (Map.Entry<Node, List<Node>> entry : outgoing.entrySet()) {
                copy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            return new Relations(Collections.unmodifiableMap(copy));
        }
    }
}

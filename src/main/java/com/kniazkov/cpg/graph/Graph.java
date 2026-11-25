package com.kniazkov.cpg.graph;

import com.kniazkov.cpg.tree.Node;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable code property graph built on top of an immutable syntax tree.
 *
 * <p>The graph keeps the root of the syntax tree and a set of separate
 * relation structures. Each structure stores only one relation type, so
 * unrelated edges never share a container. The syntax tree itself is never
 * modified: all semantic information lives in the graph.</p>
 *
 * <p>Helper methods resolve a node through a relation. For example,
 * {@link #declarationOf(Node)} returns the node that declares a variable used
 * by the given node.</p>
 */
public final class Graph {

    private final Node root;
    private final Relations declarations;
    private final Relations calls;
    private final Relations types;
    private final Relations members;
    private final Relations scopes;
    private final Relations includes;

    private Graph(Builder builder) {
        this.root = builder.root;
        this.declarations = builder.declarations.build();
        this.calls = builder.calls.build();
        this.types = builder.types.build();
        this.members = builder.members.build();
        this.scopes = builder.scopes.build();
        this.includes = builder.includes.build();
    }

    /**
     * Creates a builder for a graph rooted at the given node.
     *
     * @param root the root of the syntax tree
     * @return a new builder
     * @throws NullPointerException if {@code root} is {@code null}
     */
    public static Builder builder(Node root) {
        return new Builder(root);
    }

    /**
     * Returns the root of the syntax tree.
     *
     * @return the root node, never {@code null}
     */
    public Node root() {
        return root;
    }

    /**
     * Returns all relations keyed by their stable name.
     *
     * <p>The map preserves a fixed iteration order, so consumers can export
     * relations deterministically.</p>
     *
     * @return an immutable ordered map from relation name to relation
     */
    public Map<String, Relations> relations() {
        Map<String, Relations> map = new LinkedHashMap<>();
        map.put("declarations", declarations);
        map.put("calls", calls);
        map.put("types", types);
        map.put("members", members);
        map.put("scopes", scopes);
        map.put("includes", includes);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Returns the declaration relation.
     *
     * @return the relation from a use to its declaration
     */
    public Relations declarations() {
        return declarations;
    }

    /**
     * Returns the call relation.
     *
     * @return the relation from a call to its callee
     */
    public Relations calls() {
        return calls;
    }

    /**
     * Returns the type relation.
     *
     * @return the relation from a node to its type
     */
    public Relations types() {
        return types;
    }

    /**
     * Returns the membership relation.
     *
     * @return the relation from a node to its containing scope
     */
    public Relations members() {
        return members;
    }

    /**
     * Returns the lexical scope relation.
     *
     * @return the relation from a node to its scope
     */
    public Relations scopes() {
        return scopes;
    }

    /**
     * Returns the inclusion relation.
     *
     * @return the relation from a node to an included unit
     */
    public Relations includes() {
        return includes;
    }

    /**
     * Returns the node that declares the entity used by the given node.
     *
     * @param use the node that uses an entity
     * @return the declaring node, or an empty optional if it is unknown
     * @throws NullPointerException if {@code use} is {@code null}
     */
    public Optional<Node> declarationOf(Node use) {
        return declarations.target(use);
    }

    /**
     * Returns all nodes that declare the entity used by the given node.
     *
     * @param use the node that uses an entity
     * @return an immutable list of declaring nodes
     * @throws NullPointerException if {@code use} is {@code null}
     */
    public List<Node> declarationsOf(Node use) {
        return declarations.targets(use);
    }

    /**
     * Returns the callee of the given call node.
     *
     * @param call the call node
     * @return the callee, or an empty optional if it is unknown
     * @throws NullPointerException if {@code call} is {@code null}
     */
    public Optional<Node> calleeOf(Node call) {
        return calls.target(call);
    }

    /**
     * Returns the type of the given node.
     *
     * @param node the node
     * @return the type node, or an empty optional if it is unknown
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public Optional<Node> typeOf(Node node) {
        return types.target(node);
    }

    /**
     * Returns the scope that contains the given node.
     *
     * @param node the node
     * @return the containing scope, or an empty optional if it is unknown
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public Optional<Node> memberOf(Node node) {
        return members.target(node);
    }

    /**
     * Returns the lexical scope of the given node.
     *
     * @param node the node
     * @return the lexical scope, or an empty optional if it is unknown
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public Optional<Node> scopeOf(Node node) {
        return scopes.target(node);
    }

    /**
     * Returns the unit included by the given node.
     *
     * @param node the node
     * @return the included unit, or an empty optional if it is unknown
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public Optional<Node> includeOf(Node node) {
        return includes.target(node);
    }

    /**
     * Builds an immutable {@link Graph} instance.
     */
    public static final class Builder {

        private final Node root;
        private final Relations.Builder declarations = Relations.builder();
        private final Relations.Builder calls = Relations.builder();
        private final Relations.Builder types = Relations.builder();
        private final Relations.Builder members = Relations.builder();
        private final Relations.Builder scopes = Relations.builder();
        private final Relations.Builder includes = Relations.builder();

        private Builder(Node root) {
            this.root = Objects.requireNonNull(root, "root");
        }

        /**
         * Adds a declaration link.
         *
         * @param use the node that uses an entity
         * @param declaration the node that declares it
         * @return this builder
         */
        public Builder declaration(Node use, Node declaration) {
            declarations.add(use, declaration);
            return this;
        }

        /**
         * Adds a call link.
         *
         * @param call the call node
         * @param callee the callee
         * @return this builder
         */
        public Builder call(Node call, Node callee) {
            calls.add(call, callee);
            return this;
        }

        /**
         * Adds a type link.
         *
         * @param node the node
         * @param type the type node
         * @return this builder
         */
        public Builder type(Node node, Node type) {
            types.add(node, type);
            return this;
        }

        /**
         * Adds a membership link.
         *
         * @param node the node
         * @param owner the containing scope
         * @return this builder
         */
        public Builder member(Node node, Node owner) {
            members.add(node, owner);
            return this;
        }

        /**
         * Adds a lexical scope link.
         *
         * @param node the node
         * @param scope the lexical scope
         * @return this builder
         */
        public Builder scope(Node node, Node scope) {
            scopes.add(node, scope);
            return this;
        }

        /**
         * Adds an inclusion link.
         *
         * @param node the node
         * @param included the included unit
         * @return this builder
         */
        public Builder include(Node node, Node included) {
            includes.add(node, included);
            return this;
        }

        /**
         * Builds the graph and freezes all relations.
         *
         * @return the immutable graph
         */
        public Graph build() {
            return new Graph(this);
        }
    }
}

/* Code Property Graph: immutable snapshots and mutable construction state. MIT License. */
package com.kniazkov.cpg;

import java.util.*;

/** An immutable project snapshot, including resolved references. */
public final class Graph {
    public enum Language { CPP, JAVA }
    public enum Kind { PROJECT, FILE, INCLUDE, CLASS, METHOD, BLOCK, DECLARE, ASSIGN, RETURN,
        EXPRESSION, INTEGER, NAME, MEMBER, CALL, BINARY, UNARY, PRINT }

    /** A node belongs to a snapshot; its reference is either resolved or absent. */
    public static final class Node {
        private final Kind kind;
        private final String text;
        private final List<Node> children;
        private Node reference; // Set only during private snapshot assembly, before publication.
        private Node(Kind kind, String text, List<Node> children) {
            this.kind = kind; this.text = text; this.children = List.copyOf(children);
        }
        public Kind kind() { return kind; }
        public String text() { return text; }
        public List<Node> children() { return children; }
        public Optional<Node> reference() { return Optional.ofNullable(reference); }
        public String source(Language language) { return Source.render(this, language); }
        public Set<Language> languages() {
            EnumSet<Language> result = EnumSet.allOf(Language.class);
            if (kind == Kind.INCLUDE || (kind == Kind.MEMBER && text.contains("::"))) result.retainAll(Set.of(Language.CPP));
            if (kind == Kind.METHOD) {
                if (text.startsWith("public ")) result.retainAll(Set.of(Language.JAVA));
                if (text.equals("int main()")) result.retainAll(Set.of(Language.CPP));
            }
            for (Node child : children) result.retainAll(child.languages());
            return Collections.unmodifiableSet(result);
        }
    }

    /** A deliberately mutable node; incomplete states are allowed until build(). */
    public static final class Draft {
        private Kind kind;
        private String text;
        private final List<Draft> children = new ArrayList<>();
        private Draft reference;
        public Draft(Kind kind, String text, Draft... children) {
            this.kind = kind; this.text = text; this.children.addAll(Arrays.asList(children));
        }
        public Kind kind() { return kind; }
        public String text() { return text; }
        public List<Draft> children() { return Collections.unmodifiableList(children); }
        public Draft reference() { return reference; }
        public Draft add(Draft child) { children.add(child); return this; }
        public Draft text(String value) { text = value; return this; }
        public Draft reference(Draft target) { reference = target; return this; }
    }

    /** Mutable state for a whole graph, with reference-aware replacement and removal. */
    public static final class Builder {
        private Draft root;
        public Builder(Draft root) { this.root = root; }
        public Draft root() { return root; }
        public List<Draft> nodes() { return collect(root); }
        public void replace(Draft oldNode, Draft replacement) {
            List<Draft> before = nodes();
            if (!before.contains(oldNode)) throw new IllegalArgumentException("Node is not in this graph");
            Objects.requireNonNull(replacement);
            if (oldNode == replacement) return;
            if (root == oldNode) root = replacement;
            else for (Draft node : before) for (int i = 0; i < node.children.size(); i++)
                if (node.children.get(i) == oldNode) node.children.set(i, replacement);
            for (Draft node : nodes()) if (node.reference == oldNode) node.reference = replacement;
        }
        public void remove(Draft target) {
            List<Draft> before = nodes();
            if (!before.contains(target) || target == root) throw new IllegalArgumentException("Cannot remove this node");
            Set<Draft> removed = Collections.newSetFromMap(new IdentityHashMap<>());
            removed.addAll(collect(target));
            for (Draft node : before) if (!removed.contains(node) && removed.contains(node.reference))
                throw new IllegalStateException("Node has incoming references");
            for (Draft node : before) if (!removed.contains(node)) node.children.removeIf(n -> n == target);
        }
        /** Validates the entire state and publishes an isolated immutable snapshot. */
        public Graph build() {
            List<Draft> nodes = nodes();
            if (root.kind != Kind.PROJECT) throw new IllegalStateException("Expected project root");
            Set<Draft> members = Collections.newSetFromMap(new IdentityHashMap<>()); members.addAll(nodes);
            for (Draft node : nodes) {
                validate(node);
                if (node.reference != null && !members.contains(node.reference)) throw new IllegalStateException("Reference outside graph");
                if (node.reference != null && node.reference.kind != Kind.DECLARE && node.reference.kind != Kind.METHOD)
                    throw new IllegalStateException("Reference target must be a declaration");
            }
            Map<Draft, Node> map = new IdentityHashMap<>();
            Node immutableRoot = freeze(root, map);
            for (Draft node : nodes) map.get(node).reference = map.get(node.reference);
            return new Graph(immutableRoot);
        }
    }
    private final Node root;
    private Graph(Node root) { this.root = root; }
    public Node root() { return root; }
    public Builder mutable() {
        Map<Node, Draft> map = new IdentityHashMap<>();
        Draft copy = thaw(root, map);
        for (var entry : map.entrySet()) entry.getValue().reference = map.get(entry.getKey().reference);
        return new Builder(copy);
    }
    private static Node freeze(Draft node, Map<Draft, Node> map) {
        List<Node> children = node.children.stream().map(n -> freeze(n, map)).toList();
        Node copy = new Node(node.kind, node.text, children); map.put(node, copy); return copy;
    }
    private static Draft thaw(Node node, Map<Node, Draft> map) {
        Draft copy = new Draft(node.kind, node.text); map.put(node, copy);
        for (Node child : node.children) copy.add(thaw(child, map));
        return copy;
    }
    private static List<Draft> collect(Draft root) {
        List<Draft> out = new ArrayList<>();
        Set<Draft> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        walk(root, out, seen); return out;
    }
    private static void walk(Draft node, List<Draft> out, Set<Draft> seen) {
        if (node == null || !seen.add(node)) throw new IllegalStateException("Null, shared child, or structural cycle");
        out.add(node); for (Draft child : node.children) walk(child, out, seen);
    }
    private static boolean expression(Kind kind) {
        return Set.of(Kind.INTEGER, Kind.NAME, Kind.MEMBER, Kind.CALL, Kind.BINARY, Kind.UNARY).contains(kind);
    }
    private static void validate(Draft n) {
        Objects.requireNonNull(n.kind); Objects.requireNonNull(n.text);
        int size = n.children.size();
        int expected = switch(n.kind) {
            case INCLUDE, INTEGER, NAME -> 0;
            case METHOD, DECLARE, RETURN, EXPRESSION, MEMBER, UNARY, PRINT -> 1;
            case ASSIGN, BINARY -> 2;
            default -> -1;
        };
        if (expected >= 0 && size != expected) throw new IllegalStateException("Invalid arity: " + n.kind);
        if (n.kind == Kind.CALL && size == 0) throw new IllegalStateException("Call needs a callee");
        for (Draft c : n.children) {
            boolean valid = switch(n.kind) {
                case PROJECT -> c.kind == Kind.FILE;
                case FILE -> Set.of(Kind.CLASS, Kind.METHOD, Kind.INCLUDE).contains(c.kind);
                case CLASS -> c.kind == Kind.METHOD;
                case METHOD -> c.kind == Kind.BLOCK;
                case BLOCK -> Set.of(Kind.DECLARE, Kind.ASSIGN, Kind.RETURN, Kind.EXPRESSION, Kind.PRINT).contains(c.kind);
                default -> expression(c.kind);
            };
            if (!valid) throw new IllegalStateException("Invalid child of " + n.kind);
        }
        if (n.kind == Kind.ASSIGN && n.children.get(0).kind != Kind.NAME) throw new IllegalStateException("Invalid assignment target");
        if (n.kind == Kind.INTEGER) Integer.parseInt(n.text);
        if (n.kind == Kind.BINARY && !Set.of("+", "-", "*", "/", "<<").contains(n.text)) throw new IllegalStateException("Invalid operator");
        if (n.kind == Kind.UNARY && !Set.of("+", "-").contains(n.text)) throw new IllegalStateException("Invalid unary operator");
    }
}

package com.kniazkov.cpg.ast;

import java.util.Objects;

/**
 * A directed, typed reference from a syntax tree node to another node.
 *
 * <p>References are the non-AST edges of the code property graph. The source
 * of the reference is the node that owns it; the target is the referenced
 * node.</p>
 *
 * @param kind the kind of the reference
 * @param target the referenced node
 */
public record Reference(EdgeKind kind, Node target) {

    /**
     * Creates a reference.
     *
     * @param kind the kind of the reference
     * @param target the referenced node
     * @throws NullPointerException if {@code kind} or {@code target} is
     *         {@code null}
     */
    public Reference {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(target, "target");
    }
}

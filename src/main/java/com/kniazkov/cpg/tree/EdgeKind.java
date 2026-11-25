package com.kniazkov.cpg.tree;

/**
 * The kind of a non-AST reference between two syntax tree nodes.
 *
 * <p>AST child edges are represented by {@link Node#children()}. This enum
 * describes additional edges that turn the syntax tree into a code property
 * graph: control flow, data flow, control dependence, calls, declarations,
 * scopes, and semantic relations.</p>
 */
public enum EdgeKind {

    // Control flow edges.

    /** Sequential control flow to the next executed node. */
    NEXT,

    /** Control flow taken when a condition is true. */
    TRUE,

    /** Control flow taken when a condition is false. */
    FALSE,

    /** Control flow entering a loop body. */
    LOOP_ENTER,

    /** Control flow back to a loop condition or header. */
    LOOP_BACK,

    /** Control flow leaving a loop or switch through a break. */
    BREAK,

    /** Control flow to the next iteration of a loop. */
    CONTINUE,

    /** Control flow through a goto statement. */
    GOTO,

    /** Control flow through a return statement. */
    RETURN,

    /** Control flow through a throw expression. */
    THROW,

    /** Control flow entering a catch handler. */
    CATCH,

    /** Control flow entering a finally block. */
    FINALLY,

    // Data flow edges.

    /** A definition of a value. */
    DEF,

    /** A use of a value. */
    USE,

    /** A definition reaching a use. */
    REACHES,

    /** A data dependence between a definition and a use. */
    DATA_DEPENDENCE,

    /** An anti-dependence between a use and a later definition. */
    ANTI_DEPENDENCE,

    /** An output dependence between two definitions. */
    OUTPUT_DEPENDENCE,

    // Control dependence.

    /** A node whose execution is controlled by another node. */
    CONTROL_DEPENDENCE,

    // Calls and declarations.

    /** A call to a function or member function. */
    CALL,

    /** A declaration introduced by a node. */
    DECLARES,

    /** A reference to a declaration. */
    REFERS_TO,

    /** The type of an expression or declaration. */
    TYPE_OF,

    /** Membership in a class, namespace, or scope. */
    MEMBER_OF,

    /** An overriding declaration. */
    OVERRIDES,

    /** An implemented interface or abstract declaration. */
    IMPLEMENTS,

    /** A base class or specialization relation. */
    SPECIALIZES,

    /** A template instantiation relation. */
    INSTANTIATES,

    /** An inclusion relation, such as a header inclusion. */
    INCLUDES,

    // Scope and tree structure.

    /** A lexical scope parent. */
    SCOPE_PARENT,

    /** A lexical scope child. */
    SCOPE_CHILD,

    /** The next sibling in source order. */
    NEXT_SIBLING,

    /** The previous sibling in source order. */
    PREVIOUS_SIBLING,

    // Semantic relations.

    /** The value an expression evaluates to. */
    EVALUATES_TO,

    /** An implicit or explicit conversion. */
    CONVERTS_TO,

    /** A name binding to a declaration. */
    BINDS_TO,

    /** An alias relation. */
    ALIASES
}

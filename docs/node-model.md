<!-- CPG typed node model: agreed design decisions. MIT. -->
# Node model: agreed design decisions

Discussion began on 3 November 2026. These decisions guided the prototype's
migration to the typed model now used by the main pipeline. Proposals in the
[language-construct research](research/language-nodes/README.ru.md) are not
accepted automatically. Historical open questions below are distinguished from
the [implemented hierarchy and API](node-hierarchy.md).

## 1. Classes for constructs

Concrete constructs have dedicated Java classes and named, typed getters.
`Node`, `Expression`, `Statement` and other families support common algorithms.
The original examples included `BinaryExpression`, `CallExpression`,
`IfStatement`, and accessors `left()`, `right()`, `callee()`, `arguments()`,
`condition()`, `thenBranch()`, `elseBranch()`. Today `BinaryExpression` is abstract;
concrete operations such as `AddExpression` identify the operator. The originally
proposed `operator()` is not a `BinaryExpression` accessor.

Classes may serve several languages; substantially different constructs get
separate classes. Colour is not fixed by inheritance: supported languages can
depend on a node's contents. Nodes retain child traversal, source generation and
supported-language queries.

Published graphs are immutable; builders are mutable. Each concrete node has a
corresponding nested `Builder`, with access to the same semantic components and
mutation methods. Builder access to missing mandatory fields was initially open;
the implemented API uses `Optional` until construction.

## 2. Return values

The adopted node-accessor contract is:

- Required single values return `T`, present in a valid immutable node.
- Optional values return non-null `Optional<T>`, empty when absent.
- Collections return directly, possibly empty; never null or containing null.
- Immutable-node collections cannot change with external lists or later builder edits.

Illustrative signatures, from `IfStatement` and `CallExpression`:

```java
Expression condition();           // Required child.
Optional<Statement> elseBranch();  // Optional child.
List<Expression> arguments();      // Immutable, possibly empty.
```

This governs public node results, not internal field representation. Argument-null
handling, storage and mutable collection access were initially undecided; the
implemented builder contract is described in the hierarchy guide. This design
rule is not a claim that every auxiliary record accessor follows it: for example,
`FunctionFlow.DataEdge.callSite()` is nullable.

## 3. Nested builders and cycles

The Johnny/Freddy example established a public static nested `Builder`, replacing
the earlier proposal for separate mutable/immutable packages. Published nodes
store values and nodes, never builders. Builders own values and builder links;
later mutations cannot change earlier snapshots.

- Static `create()` produces an empty builder; setters return that same builder.
- `edit()` independently copies reachable ownership/reference structure, preserving shared identities and cycles.
- `build()` creates a fresh immutable result and validates mandatory links.
- Internally absent optional links may use null; node accessors expose `Optional`.

A build operation shares an identity map from builders to new nodes. Allocate
and register each shell before recursively filling links; revisiting a builder
returns that operation's node. Never publish unfinished shells or return a
partial graph after failure. Editing uses the inverse identity map, registering
builders before following links; matching is by object identity, not equality.

Immutability means no accessible mutation path, not necessarily final fields.
Concrete constructors are private and assembly fills fields internally. Safe
cross-thread publication and internal-method visibility were separate design
concerns; callers still need safe publication and must not edit builders concurrently.

## 4. Implementation

The parser builds `Project` through `com.kniazkov.cpg.model` builders. Passes,
generators and visualizers consume that same model. `NodeVisitor`/`Node.walk()`
provide traversal; `BuildContext`/`EditContext` preserve copying identities;
`NodeBuilder<T>` controls construction.

[Hierarchy, API and examples](node-hierarchy.md) ·
[CFG/DFG traversal](cfg-dfg.md) · [CLI and stages](../README.md)

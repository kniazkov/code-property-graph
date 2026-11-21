<!-- Typed CPG model: review guide and class hierarchy. MIT License. -->
# Typed nodes, builders and traversal

The parser and passes use `com.kniazkov.cpg.model`. [CLI commands](../README.md)
export source/SVG for a selected stage. This guide retains the original model
review's design rationale and examples; current API details supersede explicitly
identified prototype assumptions.

## Reading order

1. [Node](../src/main/java/com/kniazkov/cpg/model/Node.java) and [NodeBuilder](../src/main/java/com/kniazkov/cpg/model/NodeBuilder.java): common contracts.
2. [VariableDeclaration](../src/main/java/com/kniazkov/cpg/model/VariableDeclaration.java): fields, optional children, nested builder.
3. [NameExpression](../src/main/java/com/kniazkov/cpg/model/NameExpression.java): non-child declaration reference.
4. [MethodDeclaration](../src/main/java/com/kniazkov/cpg/model/MethodDeclaration.java) and [CallExpression](../src/main/java/com/kniazkov/cpg/model/CallExpression.java): recursive call references.
5. [BuildContext](../src/main/java/com/kniazkov/cpg/model/BuildContext.java) and [EditContext](../src/main/java/com/kniazkov/cpg/model/EditContext.java): identity-preserving assembly/copying.

## Inheritance

Selected direct relationships from the original hierarchy, not a complete census:

| Base | Derived classes |
| --- | --- |
| `Node` | `Project`, `SourceFile`, `ExternalInput`, `IncludeDirective`, `Declaration`, `Statement`, `Expression`, `TypeNode` |
| `Declaration` | `ClassDeclaration`, `CallableDeclaration`, `ParameterDeclaration`, `VariableDeclaration` |
| `CallableDeclaration` | `MethodDeclaration`, `FunctionDeclaration` |
| `Statement` | `BlockStatement`, `ReturnStatement`, `ExpressionStatement`, `PrintStatement`, `IfStatement`, `WhileStatement` |
| `Expression` | `AssignmentExpression`, `ErrorExpression`, `IntegerLiteral`, `NameExpression`, `MemberExpression`, `CallExpression`, `BinaryExpression`, `UnaryExpression` |
| `BinaryExpression` | `AddExpression`, `SubtractExpression`; also `MultiplyExpression` |
| `UnaryExpression` | `NegateExpression` |
| `MemberExpression` | `DotMemberExpression`, `PointerMemberExpression`, `ScopeMemberExpression` |
| `TypeNode` | `NamedType` |

Concrete nodes have nested builders. Declarations and statements are distinct;
blocks can contain both. Parameter placement was initially deferred, not a promise
that every declaration position accepts parameters. Callables share parameters,
return type and body; `CallExpression.target()` can select methods or functions.
Binary/unary families share operand/build logic. Member access distinguishes dot,
pointer and scope. `Language` identifies source language. `ErrorExpression`
represents missing/damaged initializers in C++03 recovery; it has no computed value
and does not permit erroneous source generation.

## Builder contract

```java
VariableDeclaration original = VariableDeclaration.create()
        .setName("count")
        .setType(NamedType.create().setName("int"))
        .setInitializer(IntegerLiteral.create().setValue(10))
        .build();
VariableDeclaration changed = original.edit()
        .setInitializer(IntegerLiteral.create().setValue(20))
        .build();
```

`original` stays unchanged. Published nodes contain no builders; private
constructors/internal assembly fill fields before returning snapshots.

- `create()` returns an empty builder; immutable getters return required values, `Optional`, or immutable lists.
- Builder single-field getters use `Optional`, even for mandatory snapshot fields: `builder.left()` differs from `node.left()`.
- Builder list getters return immutable list copies containing mutable builders.
- `setX(...)` returns the builder; lists have operations such as `setItems(...)`/`addItem(...)`.
- Setters reject null; `clearX()` clears optional fields. Internal absence may still use null.
- Each `build()` creates an independent snapshot, unaffected by later builder/input-list changes.
- `edit()` copies reachable ownership and reference targets, preserving sharing/cycles.

`NodeBuilder<T>` is abstract, centralizing final `build()` and runtime `Class<T>`
checks. Shell creation/filling remain internal implementation details, not promises
about a future API representation.

## Children and references

`children()` follows ownership, including `Project.externalInputs()` and
`ExternalInput.inputs()`. `references()` lists resolved extra targets. Both lists
are immutable/non-null; unresolved targets use dedicated optional getters and are
omitted from `references()`. Traverse `Preprocessing` records separately; external
macro definitions connect through `project.externalInput(definition.sourceKey())`.
See the [preprocessor catalogue](model-guide.ru.md#условная-компиляция-каталог-и-внешние-входы-19).

```java
var function = FunctionDeclaration.create().setName("again");
var call = CallExpression.create()
        .setCallee(NameExpression.create().setName("again").setTarget(function))
        .setTarget(function);
function.setBody(BlockStatement.create()
        .addItem(ReturnStatement.create().setValue(call)));
FunctionDeclaration snapshot = function.build();
```

This forms function → body → return → call → function-reference. Ownership
traversal remains a finite tree; following all links needs an identity-based
visited set. Shared targets require the **same builder instance**. Separate
`edit()`/`build()` calls do not merge equal-looking objects. Start whole-project
editing with `project.edit()`.

## Recovery and incomplete syntax (#21)

`SourceFile` exposes immutable `parseProblems()`, optional `stoppedAt()` and
`hasErrors()`; `Project.hasErrors()` aggregates errors. `stoppedAt()` returns a
`SourceRange`: offsets are zero-based UTF-16, lines/columns one-based.
`ParseProblem(code, message, range, skippedText, recovered)` remains a compatibility
constructor; diagnostics are records, not ownership nodes. `ErrorExpression.problem()`
links to a record; its builder/visitor exist and children are empty.

`int x = ;` can produce a structurally valid diagnostic snapshot with the variable,
an error expression, empty skipped text and zero-length range before `;`.
`edit().build()` retains records; this is neither an unfinished shell nor a valid
program. Strict `Cpg.read` rejects errors; `Cpg.readRecovering`/C++ CLI retain
syntax and JSON/SVG while blocking source generation. Unlike the initial policy
of skipping all flow analysis, current processing can analyse complete bodies
and excludes incomplete/invalid ones. See [recovery/export](model-guide.ru.md#восстановление-парсера-и-диагностический-экспорт-21)
and [flows](cfg-dfg.md).

## Typed copying contexts

`BuildContext` maps builders to nodes by identity. `copy(NodeBuilder<T>)` returns
`T`, uses checked `Class<T>.cast()` and registers before filling links.
`EditContext` maps nodes to builders; `register(Node, NodeBuilder<?>)` checks
runtime class correspondence before child copying. Java cannot express the
key-dependent map value type, so copying needs localized unchecked casts.

The prototype described both contexts as package-private and one cast site.
Currently `BuildContext` is package-private, but `EditContext` and its `copy(T)`
are public, with casts also in edge adaptation. Contexts represent one operation;
failed assembly discards its context without exposing partial shells/callbacks.

## Structural checks and editing

Build validation checks mandatory children/nonempty names and paths, link types
(e.g. callable call targets), file/block categories and assignment-target forms.
Ownership cycles or reused syntax children are rejected; shared reference targets
are allowed. Building a `Project` requires resolved targets inside its ownership
tree; standalone fragments may include reachable targets outside their subtree.

Structural validity is not compiler acceptance. Return placement, operand types,
names, modifiers and editions were separate work in the original review; do not
interpret that historical list as current missing features. Builders are not for
concurrent mutation; publish snapshots safely because private non-final fields
do not provide final-field publication guarantees.

`ProjectEditor.replace()` redirects links during copying; `remove()` checks
incoming references before list-element removal; `bind()` sets name/call targets;
`build()` returns a structurally checked project. Use `Cpg.sources(project, language)`
for generated files/entry-point adaptation.

## Assignment and Visitor

```java
var assignment = AssignmentExpression.create()
        .setTarget(NameExpression.create().setName("x"))
        .setValue(AddExpression.create()
                .setLeft(IntegerLiteral.create().setValue(2))
                .setRight(IntegerLiteral.create().setValue(3)));
var statement = ExpressionStatement.create().setExpression(assignment).build();
statement.walk(new NodeVisitor() {
    @Override
    public void visit(BinaryExpression operation) {
        System.out.println(operation.getClass().getSimpleName());
    }
});
```

Import `com.kniazkov.cpg.model.*`; examples run inside a method.
`accept(visitor)` dispatches one node; `walk(visitor)` performs root-first depth-first
ownership traversal, siblings left-to-right. Default overloads delegate to the
immediate base-class overload, so this handler sees addition. Reference targets
remain available through `references()` but are not automatically traversed.

## DOT visualization

```java
import com.kniazkov.cpg.visualization.DotVisualizer;
import java.nio.file.Path;

// Inside a method handling IOException and InterruptedException.
DotVisualizer.writeDot(statement, Path.of("tree.dot"));
DotVisualizer.renderSvg(statement, Path.of("tree.svg"));
```

`toDot(root)` returns text. Diagnostic roots/files are marked; interrupted units
show physical stopping positions/reasons while retaining visible syntax. Solid
numbered arrows identify child positions, dashed `ref` arrows resolved declarations;
external targets of selected subtrees get vertices. DOT uses Java; SVG requires
Graphviz (`dot -V`) on PATH, `dot.exe` on Windows. Launch errors/Graphviz diagnostics
propagate to callers.

## JavaDoc

Write English JavaDoc. Interfaces/abstract classes explain hierarchy roles and
method contracts; extension points identify callers, implementation duties and
invariants. Algorithms explain ordering and rationale, e.g. registering shells
before links preserves recursive identity. Leaf classes need short descriptions;
overrides need comments for additional conditions. Obvious getters/setters need
no separate comments.

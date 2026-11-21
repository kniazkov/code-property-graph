<!-- CPG: control-flow and data-flow design. MIT License. -->
# CFG and DFG

The initial flow PR introduced control/data edges, `analysis` passes and SVG
colours. Its baseline was intraprocedural: in `int y = f(x);`, DFG connects `x`
to the call and its result to `y`, separately from the call's declaration reference.
Argument-to-formal and callee-return-to-call links were initially deferred.
That is historical scope, not a current absence claim: the implementation now
also invokes `CppMemoryFlow` and supports contextual data edges. See
[DataFlow](../src/main/java/com/kniazkov/cpg/analysis/DataFlow.java) and
[FunctionFlow](../src/main/java/com/kniazkov/cpg/model/FunctionFlow.java).

## Control-flow granularity

Baseline vertices are executable statements and whole conditions. In
`if (x > 0) y = a + b;`, true reaches assignment; false reaches the following
statement, if present. DFG represents operand reads/addition dependencies.
Current CFG also uses auxiliary flow vertices for operations such as cleanup;
do not assume every endpoint is a syntax `Node`.

The initial parser slice covered Java/C++ `if` with optional `else`, `while`,
and basic comparisons, including `if (x > 0)` and `while (i < n)` through CLI
reading and flow visualization. This is not an exhaustive current parser inventory.

## Reaching definitions

A local read receives an edge from every write reaching it along some CFG path
without overwrite; joins union those definitions. For
`int x = 0; if (flag) x = 1; else x = 2; int y = x;`, the final read has sources
`x = 1` and `x = 2`, not initialization. Removing `else` also admits `int x = 0`.

Analysis iterates to a fixed point, including loop back edges. Parameters define
initial values. Assignment RHS reads may use the previous value; a write replaces
definitions only for the same resolved declaration. Java evaluates call arguments
left-to-right; the C++ path conservatively combines argument effects. The original
text referred to C++17 ordering, which is not a C++03 guarantee. Memory, callee
effects and predicate feasibility were deferred from the initial local-variable/
parameter model; that list must not be read as today's complete support boundary.

## Entry and exit

The callable declaration is entry. Its final child is its own `Exit`, available
through `exit()` and visitors at `parsed`. For analysed bodies,
`FunctionFlow.exit()` is that same object. SVG shows ownership and CFG edges;
source generation omits this service node. Empty bodies need entry-to-exit edges;
ordinary completion and returns reach exit. The initial subset used direct return
edges; cleanup can now introduce intermediate vertices. Unreachable statements
remain in the syntax tree.

## API and traversal

`ControlFlow.analyze(project)` returns a new snapshot. `DataFlow.analyze(project)`
adds data edges, computing CFG when needed; local names must be resolved.
`Project.flows()` provides per-callable results. `FunctionFlow` exposes `entry()`,
`exit()`, `control()`, `data()` and `complete()`; incomplete results publish no
partial edge lists. CFG endpoints are `FlowVertex`; data endpoints are `Node`.
They belong to the analysed snapshot. Builder edits invalidate derived facts;
recompute or inspect pass status before using them.

With an already resolved `Project project`:

```java
Project analysed = DataFlow.analyze(project);
for (FunctionFlow flow : analysed.flows()) {
    if (!flow.complete()) continue;
    for (FunctionFlow.ControlEdge edge : flow.control()) {
        FlowVertex from = edge.from(), to = edge.to();
        System.out.println(edge.kind());
    }
    for (FunctionFlow.DataEdge edge : flow.data()) {
        Node producer = edge.from(), consumer = edge.to();
        System.out.println(edge.kind());
    }
}
```

Import `com.kniazkov.cpg.model.*` and `com.kniazkov.cpg.analysis.DataFlow`.
`DataEdge.callSite()` supplies optional invocation context as a nullable reference.

## Stages and SVG

`parsed` retains syntax. Later stages (`resolved`, `green`, `folded`) run flow
analysis after their selected transformations; e.g. `--stage resolved --svg flow.svg`
shows expressions before folding. For damaged input, current processing retains
syntax and can analyse complete bodies; incomplete/invalid bodies are excluded.
It does not rewrite erroneous syntax or enable source generation.

Blue CFG edges identify transfer kinds; purple `DFG value` edges carry values,
and `DFG def` edges link reaching definitions to reads. Dashed `ref` still targets
declarations. Loop bodies explicitly return to their conditions, including empty
bodies. See [node traversal and DOT](node-hierarchy.md).

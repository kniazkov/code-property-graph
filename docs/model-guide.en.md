<!-- Code Property Graph: English model and analysis reference. MIT License. -->
# Program graph model and analysis

[Русская версия](model-guide.ru.md) · [README](../README.md)

This guide describes the public API and input grammar implemented as of 4 November 2026. The examples use Java 21. When adding a construct, update both editions, the coverage table at the end and the corresponding parser or CLI test.

## Read a project snapshot

```java
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.model.*;
import java.nio.file.Path;
import java.util.*;

// Inside a method, running from the repository root
Project project = Cpg.read(Path.of("examples/unread-write/java"),
        new Cpg.Options(Language.JAVA, Cpg.Stage.RESOLVED));
SourceFile file = project.files().getFirst();
```

The example reads a program in which the initial value of `score` is overwritten before a read. The reader processes matching files in filename order, directly inside one directory, for one selected language. A parse failure raises `parser.ParseException` with the file, line and column. A successful call returns a complete immutable project.

| Stage | What the snapshot contains |
|---|---|
| `PARSED` | Original syntax with source positions. Each callable already owns its exit. Name resolution and analysis have not run yet. |
| `RESOLVED` | Resolved names and calls, CFG, DFG and diagnostics on the original constructs. Choose this stage to inspect the input program. |
| `GREEN` | Recognized console output becomes a `PrintStatement` after resolution. Flow edges and warnings describe the transformed tree. |
| `FOLDED` | Safe constant arithmetic has also been folded. Flows and warnings are computed on the final tree. This is the CLI default. |

## Node structure and properties

The `Project` root owns `files()`. Each `SourceFile` has a path, a language and ordered top-level `items()`. Below them, declarations (`Declaration`), statements (`Statement`), expressions (`Expression`) and written types (`TypeNode`) form separate branches of the `Node` hierarchy.

| API | Purpose and example |
|---|---|
| Typed getters | `BinaryExpression.left()/right()` return operands. `VariableDeclaration.name()` returns its name and `initializer()` its optional initializer. `BlockStatement.items()` lists declarations and statements in source order. |
| `children()` | Immutable ordered list of owned children, empty for a leaf. The last child of a function or method is always its own `exit()`. |
| `references()` | Resolved references to nodes in this snapshot. For `NameExpression` and `CallExpression`, use the typed `target()`. External or ambiguous names can have an empty target. |
| `range()` | An `Optional<SourceRange>` gives the full path and a half-open source interval. Lines and columns start at 1, offsets at 0. Columns and offsets count UTF-16 code units. A synthetic `Exit` has no range. A folded literal retains the original expression span. |
| `languages()`, `source(target)` | Available direct emission targets and generated fragment text. For complete files and entry-point adaptation use `Cpg.sources(project, target)`. |
| `diagnostics()` | Immutable findings attached to this particular node. An empty list means there are no attached findings at the selected stage. |

Required children are returned directly, optional children through `Optional`, and repeated children as lists. For example, `ReturnStatement.value()` returns the expression for `return x;` and an empty optional for `return;`.

```java
project.walk(new NodeVisitor() {
    @Override public void visit(VariableDeclaration variable) {
        String type = variable.type()
                .filter(NamedType.class::isInstance)
                .map(NamedType.class::cast).map(NamedType::name).orElse("?");
        System.out.println(type + " " + variable.name());
    }
});
```

`accept(visitor)` dispatches one node. `walk(visitor)` visits the ownership tree in depth-first preorder. A family callback such as `visit(BinaryExpression)` handles addition and subtraction unless their specific overloads are overridden. Tree traversal does not follow declaration references or flow edges.

## Traversing the CFG and DFG

Read both edge sets from `project.flows()`. Each `FunctionFlow` describes one function or method with a body. Its `entry()` is the declaration and its `exit()` is the same object as `entry().exit()`. Analysis stays within the callable. A recursive call can still refer to its declaration through `CallExpression.target()`.

**CFG. Which instructions can execute next?** Each edge in `control()` has a `from()`, `to()` and `kind()`. Kinds are `ENTRY`, `NEXT`, `TRUE`, `FALSE`, `BACK` and `RETURN`. The condition expression is the decision vertex. A return transfers to the shared exit. Unreachable syntax remains in the tree but has no CFG path from entry.

```java
for (FunctionFlow flow : project.flows()) {
    Map<FlowVertex, List<FunctionFlow.ControlEdge>> outgoing = new IdentityHashMap<>();
    for (var edge : flow.control())
        outgoing.computeIfAbsent(edge.from(), v -> new ArrayList<>()).add(edge);

    Set<FlowVertex> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    Deque<FlowVertex> pending = new ArrayDeque<>();
    pending.add(flow.entry());
    while (!pending.isEmpty()) {
        FlowVertex current = pending.removeFirst();
        if (!seen.add(current)) continue;
        for (var edge : outgoing.getOrDefault(current, List.of())) {
            System.out.println(current.getClass().getSimpleName() + " --" + edge.kind()
                    + "--> " + edge.to().getClass().getSimpleName());
            pending.addLast(edge.to());
        }
    }
}
```

The visited set terminates traversal even on a loop back edge. For backward traversal, index by `to()`, start at `exit()` and follow `from()`.

**DFG. Where does this read get its value?** A `VALUE` edge runs from an operand value to its consumer, for example from a literal to the variable being initialized. A `DEFINITION` edge runs from a particular write to a name read. In the score example, the read at the print site has a `target()` pointing to the declaration `int score = 1;`, whereas its incoming `DEFINITION` comes from the assignment `score = 2`.

```java
for (FunctionFlow flow : project.flows()) {
    Map<Node, List<Node>> writesForRead = new IdentityHashMap<>();
    for (var edge : flow.data()) {
        if (edge.kind() == FunctionFlow.Dependency.DEFINITION)
            writesForRead.computeIfAbsent(edge.to(), n -> new ArrayList<>()).add(edge.from());
    }
    flow.entry().walk(new NodeVisitor() {
        @Override public void visit(NameExpression use) {
            for (Node write : writesForRead.getOrDefault(use, List.of()))
                System.out.println(use.name() + " <- " + write.getClass().getSimpleName()
                        + " " + write.range());
        }
    });
}
```

A read after a branch join can have multiple reaching writes. A back edge can carry a write from the previous iteration. To find downstream dependencies, use the same worklist as in the CFG example, index `flow.data()` by `from()` and start from the node of interest. Reverse the direction to trace a result to its sources. Use node identity within one snapshot.

The following fragment counts other nodes reachable from each assignment along outgoing DFG edges of either kind. In the score example the second write reaches the print call through the name read.

```java
for (FunctionFlow flow : project.flows()) {
    Map<Node, List<Node>> consumers = new IdentityHashMap<>();
    for (var edge : flow.data())
        consumers.computeIfAbsent(edge.from(), n -> new ArrayList<>()).add(edge.to());

    flow.entry().walk(new NodeVisitor() {
        @Override public void visit(AssignmentExpression write) {
            Set<Node> reached = Collections.newSetFromMap(new IdentityHashMap<>());
            Deque<Node> queue = new ArrayDeque<>();
            queue.add(write);
            while (!queue.isEmpty()) {
                Node producer = queue.removeFirst();
                if (!reached.add(producer)) continue;
                for (Node consumer : consumers.getOrDefault(producer, List.of()))
                    queue.addLast(consumer);
            }
            System.out.println(write.range() + " -> " + (reached.size() - 1));
        }
    });
}
```

## Pass results and editing

Passes expose results through typed methods. Name resolution populates `target()`, flow analysis populates `Project.flows()`, and diagnostic rules populate `Node.diagnostics()`. Normalization and folding change the syntax tree itself, for example by replacing a console call with a `PrintStatement`.

```java
project.walk(new NodeVisitor() {
    @Override public void visit(Node n) {
        for (Diagnostic d : n.diagnostics()) {
            String location = n.range().map(r -> r.file() + ":" + r.start().line()
                    + ":" + r.start().column()).orElse("?");
            System.out.println(location + " " + d.severity() + " [" + d.code() + "] " + d.message());
        }
    }
});
```

The `unread-write` rule attaches a `WARNING` to the particular initialization or assignment whose stored value reaches no read. The example flags the first write to `score`. Use the stable `code()` for automation and `message()` for display. JSON stores diagnostic `code` and `severity`; outbound entries in `edges` contain a kind and a numeric target ID. IDs are assigned when exporting a tree. The Java API uses direct references.

Create a node through its nested builder, e.g. `IntegerLiteral.create().setValue(2).build()`. To edit, call `edit()`, change the builder and call `build()` for a new snapshot. Editing the project discards derived flows and findings. Resolve names again when bindings are affected and run analysis on the new snapshot. For example:

```java
Project edited = project.edit().build();
Project resolved = com.kniazkov.cpg.analysis.NameResolver.resolve(edited);
Project analyzed = com.kniazkov.cpg.analysis.UnreadWrites.analyze(resolved);
```

For individual analyses, use `ControlFlow.analyze` and `DataFlow.analyze`. Resolve names before DFG analysis. `UnreadWrites.analyze` builds the DFG it needs. Each pass returns its result; query the returned project.

## Input language coverage

This table covers the normal `Cpg.read` entry point. The model also provides classes for constructs that the input parser does not yet recognize.

| Construct | Java (`.java`) | C++ (`.cpp`) |
|---|---|---|
| Top level | `class C { … }`, `public class C { … }` | `class C { public: … };`, `int main() { … }` |
| Class method | `public static void main(String[] args)` | `static void main()` |
| Local declarations | `int x = expression;` | `int x = expression;` |
| Literals and arithmetic | Decimal signed 32-bit integers, grouping, unary and binary `+`, `-`, binary `*`, `/` | Same, plus `<<` |
| Assignment and comparison | `=` (including chains), `== != < <= > >=` | Same |
| Access and calls | `obj.member`, `f(a, b)`, `C.main(args)` | `obj.member`, `f(a, b)`, `C::main()` |
| Control | Blocks, `if`, `if/else`, `while`, `return;`, `return expr;` | Same |
| Comments and directives | `//`, `/* … */` | Both comment forms and preserved `#include <…>`, `#include "…"` |
| Console normalization | `System.out.println(x)` | `std::cout << x << std::endl` |

The Java main parameter name can vary. Coverage describes syntax recognition; full compiler type and semantic checks are outside its scope. C++ headers are preserved without expansion. General function signatures, constructors, templates, general arrays, strings, `for`, `break` and `continue` are outside the current input grammar.

**Model nodes available ahead of the parser** include remainder `%`, right shift `>>`, logical negation `!`, address-of `&x`, dereference `*p` and pointer member access `p->field`. C, Python, JavaScript and Ascend C have no input parser yet. Cross-language generation covers small Java/C++ programs with the supported main and console forms. See the README for translation constraints.

[Grammar implementation](../src/main/java/com/kniazkov/cpg/parser) · [Analysis passes](../src/main/java/com/kniazkov/cpg/analysis) · [Model classes](../src/main/java/com/kniazkov/cpg/model)

The [C++03 file corpus](e2e.en.md) now checks the public API and packaged CLI against the same ordinary source files, with strict GCC and Clang execution oracles. Its initial coverage is a return literal, arithmetic/console normalization, and an invalid-token diagnostic; it does not extend the supported language surface.

The [SQUARE fixture](../src/test/resources/cpp03/square/README.md) adds a reproducible two-unit macro project and compilation database. Strict native execution verifies 58 before editing and 64 for a hand-written edited oracle. Import and macro preprocessing are implemented by #13–#15; graph provenance and macro-aware editing are implemented in #16–#17.

## C++03 edition and compilation databases

`new Cpg.Options(Language.CPP, stage)` retains the default C++03 behavior.
The three-argument form accepts `"c++03"` as an explicit override; `null` means
no override. Only C++03 is supported. Explicit unsupported editions fail even
for empty input. The existing three-argument `Parser.parse` remains compatible;
its fourth argument can select the edition.

`CompilationDatabase.read(path)` creates an immutable `Build` in
`com.kniazkov.cpg.build`. `Cpg.readCompilationDatabase(path, options)` imports
and parses it; `Cpg.read(build, options)` uses an explicitly constructed build.
Each `Build.TranslationUnit` records a `PhysicalSource`, working directory,
ordered `IncludePath` entries and ordered `MacroOperation` define/undefine
operations, and its command's standard. `IncludeInstance` identifies an
occurrence separately from physical source and translation-unit context.
These are input records, not new syntax nodes or claims of implemented macro
expansion. Header processing is still pending.

The effective standard is the explicit API/CLI override, then the record's last
`-std`, then C++03. A C++11 database entry fails with its filename unless it is
overridden; the override does not permit C++11 syntax. `arguments` takes
precedence over `command`. Command quoting is parsed without launching a shell,
performing expansion, or reading response files. Unsupported parsing options
report the entry's filename. Joined/separate `-I`, `-isystem`, `-iquote`, `-D`,
`-U`, and `-std` forms retain order. Standard output/dependency/diagnostic flags
are ignored only when explicitly recognized; unsupported flags are rejected.

Relative `directory` is based on the database's directory. Relative `file` and
include paths are based on that entry's working directory, not the process cwd.
Folder input remains supported. CLI uses `--compile-commands FILE` instead of
`--input DIR`, with optional `--cpp-standard c++03`. See the
[reproducible database fixture](../src/test/resources/cpp03/compilation-database/README.md).

## C++03 lexical phases and physical positions

`CppLexer.scan(file, source)` provides an immutable `Result` with phase-3 logical
`text()`, `tokens()`, per-logical-character `characterRanges()`, and physical
`eof()`. Input is a decoded Java String; offsets and columns count UTF-16 code
units. CRLF/CR normalize to logical newlines. Trigraph replacement runs before
a single pass of backslash-newline splicing. Comments become one space without
joining neighboring tokens. A block comment's internal physical newlines do not
terminate a logical preprocessing directive; a line comment ends at the next
logical newline, after splicing.

Each public `PreprocessingToken` has a kind, retained `spelling()`, separate
`canonicalSpelling()`, physical range and half-open logical offsets. Whitespace,
newlines and EOF remain explicit for preprocessing. The physical range
of a spliced token spans its original physical fragments; character mappings
identify the individual surviving characters. A replaced trigraph maps to all
three original characters. Comment replacement maps to the full comment range.

The lexer distinguishes identifiers/UCNs, preprocessing numbers, ordinary/wide
character and string literals, header names, operators and other characters.
It retains digraph/alternative operator spelling while canonicalizing parser
input, and uses the C++03 Annex E identifier repertoire rather than modern Java
identifier rules. Maximal munch uses C++03 rules: e/E signs belong to pp-numbers;
modern p/P sign rules, `<::` exceptions and template `>>` splitting are not added.
Header-name tokens occur only in include-directive context. Partial literals,
comments and headers report physical locations.

The existing Java lexer is unchanged. The C++ parser consumes the new token
stream, while its supported syntax remains deliberately smaller than the
lexical repertoire. Tokenizing a literal or operator does not claim evaluation,
full expression support, header resolution or macro expansion. See
[lexical acceptance](../src/test/resources/cpp03/lexical-phases/README.md) and
[negative literal acceptance](../src/test/resources/cpp03/unterminated-literal/README.md).

## Header inclusion and macro expansion (C++03 #15)

`CppPreprocessor.preprocess(Build.TranslationUnit, source)` applies ordered `-D`/`-U` operations independently per translation unit and runs lexical phases on each included physical file. Object/function macros support empty replacement lists, nested argument parentheses, argument prescan, rescan with recursive suppression, raw `#` stringization and `##` token pasting. A pasted result must be one preprocessing token. C++03 empty actual arguments have undefined behavior and are diagnosed; a genuinely zero-parameter `F()` and arguments becoming empty after expansion are supported.

Quoted headers search the including file's directory, `-iquote`, `-I`, then `-isystem`; angle headers search `-I` then `-isystem`. Macro-generated include operands are expanded first. Include guards use `#ifdef`/`#ifndef`/`#else`/`#endif`; full `#if` expressions remain #19. Repeated includes run in the current macro context; missing files report the full include chain. Only the existing modeled `<iostream>` remains opaque when no configured file exists. Other missing system headers are errors, not silently ignored. Include nesting and expansion work have bounded diagnostic limits.

The parser consumes a single active expanded stream with per-token physical filenames and primary expansion ranges. Preprocessor results expose spelling ranges, expansion ranges, macro origins and include occurrence identities; graph/JSON/SVG provenance is described in the #16 section below. Macro-aware source preservation is described in the #17 section below. Minimal no-argument `int name()` declarations/definitions support the two-unit SQUARE fixture; full function semantics and cross-unit lookup are not claimed. `CppPreprocessorTest`, `PreprocessorIT`, and the `square`, `macros`, `missing-header` corpus fixtures cover these boundaries.

## Macro provenance API, JSON and SVG (#16)

`Node.provenance()` returns an optional `TokenProvenance`: a half-open final-token interval and its immutable `Preprocessing` translation-unit record. `tokens()` exposes spelling/expansion ranges, include occurrence IDs and expansion IDs; `expansions()` follows non-owning event links. `SourceFile.provenance().unit()` retains every event, including macros producing no AST. `Node.children()`, `references()` and ordinary visitors remain syntax/resolution-only. Edit/build copies the immutable provenance; analysis results still follow their existing invalidation rules.

Each expansion has a unique TU-local ID, current definition and replacement fragments, full invocation range, raw actual argument fragments, parent ID, #/## contributing fragments, and half-open output token boundaries. Reused prescanned arguments retain one event but may contribute several output token runs; the interval is the enclosing boundary, while each token's expansion IDs give exact membership. Empty events have an equal-boundary anchor. Includes remain occurrence-specific. This is source provenance; generation separately validates whether an unchanged macro call can be retained.

JSON stores `preprocessing` once per source file. Each node's `provenance.sourceFileId` scopes its token interval and `expansionIds`; all range objects carry physical `file`. IDs are deterministic for identical inputs and separate across translation units. SVG shows purple dashed origins, parent expansion, invocation, definition, replacement/operator, argument and #/## operand edges. Use `DotVisualizer.toDot(root, false)`, `renderSvg(root, path, false)` or CLI `--provenance off` to hide only this layer. The default is on.

[Reproduction and expected fragments](../src/test/resources/cpp03/provenance/README.md): SQUARE(a) has two reads/one argument, NEST(f()) two calls/one argument, and the '*' token leads to the header. Partial, multi-statement and empty replacements are tested independently of whole-subtree macros. Full provenance after arbitrary transformations remains part of the later history work.

Command-line macro definitions use explicit synthetic source buffers named `<command-line:N>` in operation order. Their ranges index that normalized definition buffer, not the translation unit file; invocation ranges still point to real source. Include IDs retain the translation-unit context.

## Editing one macro expansion (#17)

Use `ProjectEditor.replace` on the chosen expanded operand, then `Cpg.sources(edited, Language.CPP)`. A fresh replacement inherits missing source range/token provenance, while explicit replacement provenance is retained. The original snapshot remains immutable. For C++ snapshots with macros or physical includes, `Preprocessing.sources()` captures original physical text and `configuration()` retains the translation-unit settings; generation never rereads disk.

Generation compares the edited syntax against an in-memory replay, widens an edit inside a macro to a representable outer invocation, emits that edited subtree with parentheses, and patches the captured source text. Unaffected calls, directives and headers retain their bytes. The entire result is preprocessed/parsed again under a distinct virtual output root and compared with the desired effective syntax before any files are returned. Captured relative header paths are included in the output map; the CLI creates parent directories after checking output collisions. Callers retain the relevant compilation flags/database for rebuilding the generated project.

The supported edit is deliberately bounded: a macro fragment without a whole-subtree replacement boundary, structural edits that would flatten unrelated calls, conflicting shared-source edits, headers outside the common source root, absolute includes, and include paths that cannot resolve after relocation are rejected clearly. No incomplete source set is returned. General multi-configuration conflict handling and editing arbitrary source constructs remain later tasks. Legacy no-macro/no-header generation and cross-language adaptation retain their existing formatting behavior.

The [runnable public-API demo](../examples/cpp03-square/README.md) changes only SQUARE(a)'s right operand to b: original/generated 58, edited/generated 64, byte-identical header and second.cpp, then CLI reparse gives a*b and c*c. Nested OUT(a) replacement with b+1 preserves multiplication/addition precedence. Corpus tests require both strict GCC and Clang; the manual demo is also run in CI. This completes the first vertical milestone, not the full C++03 checklist.

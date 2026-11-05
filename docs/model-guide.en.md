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

The example reads a program in which the initial value of `score` is overwritten before a read. The reader processes matching files in filename order, directly inside one directory, for one selected language. The strict `Cpg.read` entry point raises `parser.ParseException` with the file, line and column on a parse failure. A successful strict call returns a complete immutable project. For diagnostic snapshots of damaged C++03 input, use the separate [recovering API](#parser-recovery-and-diagnostic-exports-21).

| Stage | What the snapshot contains |
|---|---|
| `PARSED` | Original syntax with source positions. Each callable already owns its exit. Name resolution and analysis have not run yet. |
| `RESOLVED` | Resolved names and calls, CFG, DFG and diagnostics on the original constructs. Choose this stage to inspect the input program. |
| `GREEN` | Recognized console output becomes a `PrintStatement` after resolution. Flow edges and warnings describe the transformed tree. |
| `FOLDED` | Safe constant arithmetic has also been folded. Flows and warnings are computed on the final tree. This is the CLI default. |

## Node structure and properties

The `Project` root owns `files()` and the non-syntax configuration tree `externalInputs()` (see #19 below). Each `SourceFile` has a path, a language and ordered top-level `items()`. Below them, declarations (`Declaration`), statements (`Statement`), expressions (`Expression`) and written types (`TypeNode`) form separate branches of the `Node` hierarchy.

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
| Comments and directives | `//`, `/* … */` | Both comment forms; includes, macros and conditional directives (see #15–#19 below) |
| Console normalization | `System.out.println(x)` | `std::cout << x << std::endl` |
| Damaged input | Strict failure | Strict `Cpg.read`; opt-in diagnostic recovery with `ErrorExpression` (see #21) |

The Java main parameter name can vary. Coverage describes syntax recognition; full compiler type and semantic checks are outside its scope. Configured C++ headers are expanded; preprocessing coverage is described separately below and does not expand the syntax grammar. General function signatures, constructors, templates, general arrays, strings, `for`, `break` and `continue` are outside the current input grammar.

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

Quoted headers search the including file's directory, `-iquote`, `-I`, then `-isystem`; angle headers search `-I` then `-isystem`. Macro-generated include operands are expanded first. Conditional groups and full preprocessing expressions are described in the #19 section below. Repeated includes run in the current macro context; missing files report the full include chain. Only the existing modeled `<iostream>` remains opaque when no configured file exists. Other missing system headers are errors, not silently ignored. Include nesting and expansion work have bounded diagnostic limits.

The parser consumes a single active expanded stream with per-token physical filenames and primary expansion ranges. Preprocessor results expose spelling ranges, expansion ranges, macro origins and include occurrence identities; graph/JSON/SVG provenance is described in the #16 section below. Macro-aware source preservation is described in the #17 section below. Minimal no-argument `int name()` declarations/definitions support the two-unit SQUARE fixture; full function semantics and cross-unit lookup are not claimed. `CppPreprocessorTest`, `PreprocessorIT`, and the `square`, `macros`, `missing-header` corpus fixtures cover these boundaries.

## Macro provenance API, JSON and SVG (#16)

`Node.provenance()` returns an optional `TokenProvenance`: a half-open final-token interval and its immutable `Preprocessing` translation-unit record. `tokens()` exposes spelling/expansion ranges, include occurrence IDs and expansion IDs; `expansions()` follows non-owning event links. `SourceFile.provenance().unit()` retains every event, including macros producing no AST. Preprocessing records are not syntax children or resolution references. Ordinary visitors also traverse the project-owned external configuration nodes added in #19. Edit/build copies the immutable provenance; analysis results still follow their existing invalidation rules.

Each expansion has a unique TU-local ID, current definition and replacement fragments, full invocation range, raw actual argument fragments, parent ID, #/## contributing fragments, and half-open output token boundaries. Reused prescanned arguments retain one event but may contribute several output token runs; the interval is the enclosing boundary, while each token's expansion IDs give exact membership. Empty events have an equal-boundary anchor. Includes remain occurrence-specific. This is source provenance; generation separately validates whether an unchanged macro call can be retained.

JSON stores `preprocessing` once per source file. Each node's `provenance.sourceFileId` scopes its token interval and `expansionIds`; all range objects carry physical `file`. Event IDs are deterministic for identical inputs and separate across translation units; expanded date/time values additionally depend on the captured translation time. SVG shows purple dashed origins, parent expansion, invocation, definition, replacement/operator, argument and #/## operand edges. Use `DotVisualizer.toDot(root, false)`, `renderSvg(root, path, false)` or CLI `--provenance off` to hide only this layer. The default is on.

[Reproduction and expected fragments](../src/test/resources/cpp03/provenance/README.md): SQUARE(a) has two reads/one argument, NEST(f()) two calls/one argument, and the '*' token leads to the header. Partial, multi-statement and empty replacements are tested independently of whole-subtree macros. Full provenance after arbitrary transformations remains part of the later history work.

Command-line macro definitions use explicit synthetic source buffers named `<command-line:N>` in operation order. Their ranges index that normalized definition buffer, not the translation unit file; invocation ranges still point to real source. Include IDs retain the translation-unit context.

## Editing one macro expansion (#17)

Use `ProjectEditor.replace` on the chosen expanded operand, then `Cpg.sources(edited, Language.CPP)`. A fresh replacement inherits missing source range/token provenance, while explicit replacement provenance is retained. The original snapshot remains immutable. For C++ snapshots with macros or physical includes, `Preprocessing.sources()` captures original physical text and `configuration()` retains the translation-unit settings; generation never rereads disk.

Generation compares the edited syntax against an in-memory replay, widens an edit inside a macro to a representable outer invocation, emits that edited subtree with parentheses, and patches the captured source text. Unaffected calls, directives and headers retain their bytes. The entire result is preprocessed/parsed again under a distinct virtual output root and compared with the desired effective syntax before any files are returned. Captured relative header paths are included in the output map; the CLI creates parent directories after checking output collisions. Callers retain the relevant compilation flags/database for rebuilding the generated project.

The supported edit is deliberately bounded: a macro fragment without a whole-subtree replacement boundary, structural edits that would flatten unrelated calls, conflicting shared-source edits, headers outside the common source root, absolute includes, and include paths that cannot resolve after relocation are rejected clearly. No incomplete source set is returned. Multi-configuration reconciliation is described in #20 below; editing arbitrary source constructs remains outside this bounded patching contract. Legacy no-macro/no-header generation and cross-language adaptation retain their existing formatting behavior.

The [runnable public-API demo](../examples/cpp03-square/README.md) changes only SQUARE(a)'s right operand to b: original/generated 58, edited/generated 64, byte-identical header and second.cpp, then CLI reparse gives a*b and c*c. Nested OUT(a) replacement with b+1 preserves multiplication/addition precedence. Corpus tests require both strict GCC and Clang; the manual demo is also run in CI. This completes the first vertical milestone, not the full C++03 checklist.

## Translation-unit variants and build compositions (#18)

`Build.TranslationUnit.variantId()` hashes the normalized physical path, working directory, effective language standard, ordered include paths and macro operations. C++03 default and explicit C++03 agree; standard overrides produce matching effective identities. Exact duplicate configurations are deduplicated. IDs are stable for the same absolute inputs, not portable across relocated source roots.

`Cpg.readCompilationDatabase` retains all configurations in one `Project`. `Project.build()` exposes inputs, `variants(Path)` enumerates owned versions, and `SourceFile.variantId()` distinguishes them. Construct `Build.Composition(name, variantIds)` and call `project.selectBuild(composition)` to obtain a restricted immutable snapshot with fresh resolution, diagnostics and flows. Select each alternative from the original full snapshot. Metadata survives edit/build, diagnostics and flow attachment; unknown IDs, duplicate selection and competing configurations of one file are rejected.

`CallExpression.candidates()` exposes possible targets, `resolution()` distinguishes unresolved/resolved/ambiguous/ODR-conflict, and compatible `target()` is present only for one resolved target. Local qualified members prefer their own TU, so repeated in-class definitions from a common header do not create false alternatives. Unknown composition exposes all matching cross-TU free-function definitions when no local definition wins. Known composition restricts lookup to selected units and diagnoses competing free-function definitions; duplicate definitions inside one TU are conflicts even without a named build. Full overload, linkage, class/template ODR and entity-merging rules remain later tasks.

JSON includes build units/configurations, optional named composition, file variant IDs, call resolution and candidate node IDs. Canonical or cross-language output requires selecting one composition for competing configurations of the same physical path. Preserved C++ output can reconcile multiple variants as described in #20 below; JSON-only CLI import retains the complete graph. The [SERVER/CLIENT demo](../examples/cpp03-builds/README.md) proves four retained units, isolated local calls/flows, two unknown run() candidates, and two explicitly selected programs built with their own macro flags.

## Conditional compilation, catalog and external inputs (#19)

`#if`/`#ifdef`/`#ifndef`/`#elif`/`#else`/`#endif` select one active token stream before syntax parsing. Branch boundaries may split an expression or declaration. Inactive text creates no AST, CFG, DFG or syntax diagnostics, expands no macros and opens no headers. Lexical phases still run, so an unterminated comment is an error even in a skipped branch. Conditional nesting is checked; conditions nested under an inactive parent and later arms after a selected arm are not evaluated.

Obtain the immutable catalog with `file.provenance().orElseThrow().unit()`:

- `definitions()` lists every executed definition, including unused and superseded definitions. Entries retain `id`, `name`, `functionLike`, `parameters`, `rawReplacement`, physical `range`, `includeId`, `originKind` and `sourceKey`. Replacement text retains internal whitespace/comments. A new definition after `#undef` gets its own ID. Filter `expansions()` by `definitionId()` to navigate from one definition to its expansions.
- `includeStates()` records the active name-to-definition-ID map **after each include occurrence completes**, including the root. It is the whole visible environment at that point, not just definitions written in that header. Resolve IDs through `definitions()` and filter by definition `includeId` for definitions originating in one header occurrence. It is not an arbitrary-time macro-state query.
- `presenceChecks()` records processed `#ifdef`, `#ifndef` and `defined` uses independently of value expansion. `present` reports definition existence; `conditionResult` negates it for `#ifndef`. For `defined`, it is that operator's result, not the whole `#if` expression. `definitionId` is nullable when absent; `branchId` identifies the containing arm. `-DDEBUG=0` makes `#ifdef DEBUG` true without creating an expansion. A skipped nested condition creates no check. All `defined` operands of an evaluated directive are recorded during preprocessing, even in an operand later skipped by arithmetic short-circuiting.
- `branches()` retains each arm's `directive`, physical `directiveRange`, `rawSource`/`range`, parent/include IDs, `parentActive`, `evaluated`, `conditionResult`, `active` and half-open final-token boundaries. The raw arm includes its opening directive and ends before the next sibling directive or closing `#endif`; enclosing arms can include nested groups. `directives()` retains directive text, including closing `#endif`, null directives and ignored pragmas. `configuration().variantId()` supplies the build context.

`Project.externalInputs()` owns a tree of `ExternalInput` nodes with `key`, `role`, `text` and `inputs()`. They have builders, participate in `children()`/`NodeVisitor`, and survive snapshot edits even when no source uses them. Database definitions have the ownership path project → compilation database → command entry → macro argument. Exact arguments such as `-DDEBUG=0` are retained; split arguments retain the operand separately. Java API and CLI configuration roots are distinguished, and predefined macros belong to the C++03 language-profile root. `project.externalInput(definition.sourceKey())` resolves an external definition to the actual owned node in that snapshot. Source-file definitions instead retain their physical file key/range and do not require an external node.

JSON adds `externalInputs` to the project and catalog arrays to each source file's `preprocessing`. Definitions include reverse `expansionIds`/`presenceCheckIds` and, for external definitions, `sourceNodeId`; expansions/checks carry `definitionId`, and branches carry `variantId`. Presence checks inherit their variant through their source file/branch rather than duplicating it. SVG adds catalog, branch, presence and configuration-origin links to the optional provenance layer. API/CLI comparisons preserve equivalent syntax, analyses and effective preprocessing, but direct API versus CLI input attribution intentionally differs (`java-api` versus `cli`); it is not a promise of byte-identical full JSON for differently supplied inputs.

`#line` macro-expands its operands, accepts line numbers 1–32767 and an optional ordinary string filename, and changes subsequent logical coordinates without rewriting physical ranges. `logicalLocations()` records that mapping per include occurrence. `__FILE__`/`__LINE__` use the invocation's logical location, `__cplusplus` expands to `199711L`, and `__DATE__`/`__TIME__` use one captured UTC translation time. The preprocessor overload accepting `Instant` makes captures reproducible; source-generation replay retains that time. Dynamic builtin definitions have an empty `rawReplacement`; their evaluated replacement belongs to the expansion/token stream. This profile leaves `__STDC__` undefined. Active `#error` fails, null directives are accepted, and all pragmas are retained but ignored, including `#pragma once`. Redefining/undefining reserved preprocessing names and macro-generated `defined` are diagnosed rather than assigned a meaning for C++03 undefined behavior.

The preprocessing expression evaluator is separate from the smaller syntax parser. It supports C++03 integer bases and `U`/`L` suffixes, ordinary/wide character constants, `true`/`false`, unary arithmetic/logical operators, arithmetic, shifts, comparisons, bitwise/logical operators and `?:` with precedence and short-circuiting. Remaining identifiers/keywords become zero, except boolean literals and alternative operator tokens. Skipped operands are still syntax-checked. Floating literals, `LL`/binary literals, casts, calls, assignment and comma expressions are rejected.

Implementation-defined choices are fixed here, not inferred from the host compiler and not yet a configurable target profile: signed/unsigned preprocessing integers are 64 bits, unsigned arithmetic wraps, signed division truncates toward zero, signed right shift extends the sign, and left shift keeps the low 64 bits. Evaluated signed arithmetic overflow, division by zero and shift counts outside 0–63 are diagnosed. Ordinary character values use UTF-8 bytes, numeric escapes use their low byte, and multicharacter constants pack the low 64 bits. Wide constants use Unicode scalar values, signed 32-bit numeric escapes, and the final character of a multicharacter literal. These choices may differ from a native compiler and do not define the future ordinary-expression type/encoding model.

Unchanged conditional source is regenerated byte-for-byte, including inactive fragments. Acceptance is covered by `CppConditionalExpressionTest`, `CppPreprocessorTest`, `ConditionalCatalogModelTest`, `ConditionalCatalogIT` and the mandatory [conditional-catalog fixture](../src/test/resources/cpp03/conditional-catalog/README.md). Editing an active branch while validating all alternate builds and shared-source conflicts is described in #20 below.

## Shared headers across configurations (#20)

Pass the complete set of variants to `Cpg.sources(edited, Language.CPP)` when exporting one shared physical source tree. For captured C++ preprocessing inputs, generation reconciles physical edits before returning any files. A variant whose active fragment was not edited still requires the original text. Changing a shared `return 1;` literal to `2` only for SERVER therefore conflicts with an unchanged CLIENT occurrence. The diagnostic identifies the physical file/range, affected variant identities/configuration labels and required replacement texts. Choosing an explicit composition limits the requirements to that selected snapshot; it is not evidence that unexported variants remain unchanged.

Identical requirements emit one physical patch. A fragment inactive in a variant imposes no active-syntax requirement, so changing the SERVER return inside an existing `#ifdef SERVER` preserves the CLIENT `#else` bytes and directive boundaries. The generator does not invent a conditional directive to resolve a conflict. Structural edits that would consume preprocessing directives or cannot be represented as safe physical patches are rejected. Final replay still validates all exported variants against the desired syntax using the captured inputs, without rereading original files.

Obtain the entire source map before creating output directories or writing source/JSON/SVG files. `Cpg.sources` is an in-memory operation; the CLI and [public editing example](../examples/cpp03-shared-headers/README.md) perform this validation before their export phase. Canonical and cross-language generation still require a selected composition when competing variants cannot be preserved as one source tree.


## Parser recovery and diagnostic exports (#21)

`Cpg.readRecovering(Path, Options)`, `Cpg.readRecovering(Build, Options)` and `Cpg.readCompilationDatabaseRecovering(Path, Options)` retain a diagnostic C++03 snapshot when source parsing fails. The corresponding `Cpg.read` / `readCompilationDatabase` methods remain strict and throw instead of returning a damaged graph. Java parsing remains strict in both facade routes. Invalid compilation settings, unsupported standards/options and translation-unit input I/O failures are not source recovery and still fail without a graph.

```java
Project project = Cpg.readCompilationDatabaseRecovering(
        Path.of("src/test/resources/cpp03/parser-recovery/compile_commands.json"),
        new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED));
if (project.hasErrors()) {
    for (SourceFile file : project.files()) {
        for (ParseProblem problem : file.parseProblems()) {
            System.out.println(problem.code() + ": " + problem.message());
        }
        file.stoppedAt().ifPresent(System.out::println);
    }
}
```

For one source buffer, the lower-level `Parser.parseRecovering(file, source, language[, standard])` returns a `SourceFile.Builder`; this explicitly selected parser API also supports Java recovery. The configured C++ overload accepts `Build.TranslationUnit` and can replay supplied source contents and translation time. Lexical/preprocessing failures preserve the available token prefix and configuration/provenance, without promising recovery of arbitrary malformed syntax. Missing includes are source diagnostics, not invalid build options.

Each `SourceFile.parseProblems()` is an immutable list of `ParseProblem` values. The original five fields and constructor, `ParseProblem(code, message, range, skippedText, recovered)`, remain available; #22 adds primary position and occurrence context below. `range` is a physical `SourceRange`; `skippedText` retains the raw physical fragment rather than reconstructed tokens. If `#line` gives a different logical location, the message retains it as an explicit note without changing the physical range. `recovered` means parsing continued at a known boundary, not that the program became valid. `SourceFile.hasErrors()` and `Project.hasErrors()` expose the error status. `SourceFile.stoppedAt()` is an optional zero-length physical range at the point where that unit stopped. These records persist through ordinary `edit().build()` copying and are separate from derived analysis diagnostics.

Recovery is deliberately narrow. A missing or malformed variable initializer can become an `ErrorExpression` only when the parser reaches its semicolon without crossing a brace, unmatched parentheses/brackets, or a strict-edition violation; the declaration remains and following statements can be parsed. `ErrorExpression.problem()` retains the diagnostic, and the node participates in builders, `children()` traversal and `NodeVisitor` as a leaf expression. For `int x = ;`, its range has zero length immediately before `;` and `skippedText` is empty. For `int x = 1 + ;`, the range starts at the first initializer token and ends immediately before the semicolon, preserving interior and trailing whitespace. For an unrecovered failure, `range` and `skippedText` describe the suffix from the failure position to that physical file’s EOF, while `stoppedAt` identifies just the start. When no reliable boundary exists, the unit stops instead of guessing how to resume; completed top-level declarations and statements inside an opened callable/block, along with collected problems, remain, and other translation units continue under their own build settings. Recovery must consume input, finish the current construct, or stop, including at EOF.

A project with any parse error stays at the `PARSED` result even if `RESOLVED`, `GREEN` or `FOLDED` was requested. Resolution, transformations, CFG, DFG and analysis warnings are skipped for the whole project; usable neighboring syntax is retained, but no sound flow result is claimed. Per-function analysis of complete regions and diagnostic repair after graph editing remain separate planned work (#31 and #43). `Cpg.sources` and the source renderers reject the damaged graph for every target language before returning source text.

The C++ CLI uses the recovering route. It reports physical file/line/column diagnostics, writes requested `--json` and `--svg` diagnostic exports, and exits with status 1. With `--out` also requested, no source directory or files are created. Diagnostic destinations must be separate from the blocked source directory; conflicting destinations fail before writes. Invalid build configuration and Java parse failures still produce no exports. JSON adds `hasErrors: true` to the project and affected files, `parseProblems` to affected files, optional `stoppedAt`, and `problem` to each `ErrorExpression`. A healthy snapshot omits these fields, preserving existing golden snapshots. SVG marks the project error, each affected file and its stop position while showing available nodes from all units.

This does not enable compiler extensions. Active `__attribute__((packed))`, variadic macros, `LL` and binary literals, and raw string literals are rejected in C++03 with a location and construction-specific reason. The attribute is diagnosed at `__attribute__` (column 8 in `struct __attribute__((packed)) ...`), rather than at `struct`. The same declaration in an inactive `#if 0` branch produces no syntax node or syntax error and can be preserved through generation. GCC/Clang dialect support is not implemented.

`ParserRecoveryIT` exercises the packaged CLI, strict/recovering API distinction, zero-length and nonempty skipped fragments, independent translation units, actual JSON/SVG exports, extension boundaries and finite termination. `ParserRecoveryTest` covers model copying and Visitor dispatch; `RecoveryExportTest` covers JSON, DOT and generation guards. The mandatory [parser-recovery](../src/test/resources/cpp03/parser-recovery/README.md) and [inactive-extension](../src/test/resources/cpp03/inactive-extension/README.md) fixtures provide repeatable commands.

## Diagnostic provenance and grouping (#22)

`ParseProblem` adds `primaryRange`, `reason`, `relatedRanges` and `context` to its five recovery fields. `primaryRange` identifies the physical cause shown to a user; it need not equal the skipped recovery fragment in `range`. `reason` is a stable cause including significant parameters, without incidental presentation/context notes. `DiagnosticRelatedRange(role, label, range)` supplies a machine-readable role, explanatory label and physical range, possibly in another file. All ranges use inclusive starts and exclusive ends. The compatibility constructor uses `range` as `primaryRange`, `message` as `reason`, an empty related list and null context.

`DiagnosticContext(translationUnit, variantId, includeChain, expansionChain)` identifies the normalized absolute physical translation-unit path, optional build variant, and outermost-first lists of `Preprocessing.Include` and `Preprocessing.Expansion` values. A null `variantId` represents an unconfigured unit. The lists are immutable; their IDs belong to that translation unit/configuration and are not global node IDs. They contain value records, not references to syntax nodes. Expansion values retain invocation, definition, replacement fragments, arguments and #/## operations. Ordinary `edit().build()` and repeated analysis therefore preserve this evidence without stale node references; this does not claim to repair an error after editing its source (#43).

`Project.parseProblems()` returns every occurrence sorted by physical path/range, code, reason and complete context, with stable tie-breakers. It supplies the owning translation unit when a legacy five-argument record has no context; `SourceFile.parseProblems()` still exposes the file's original records. `Project.diagnosticGroups()` groups these occurrences by **code + physical `primaryRange` + `reason`**, not by message text or line alone. Each `DiagnosticGroup` exposes `code`, `primaryRange`, `reason`, `message`, sorted `occurrences`, and three separate counts:

- `translationUnitCount()`: distinct physical translation-unit paths.
- `variantCount()`: distinct (translation-unit path, variant ID) pairs; an unconfigured unit has one variant.
- `occurrenceCount()`: every occurrence, including repeated includes and configurations.

Thus a common-header error seen from two `.cpp` files has one group and two affected translation units. Two configurations of one physical `.cpp` still count as one translation unit but two variants. Different invalid paste results such as `+*` and `+/` at the same physical invocation remain different reasons/groups. Group and occurrence order do not depend on translation-unit traversal order; this guarantee concerns diagnostic summaries, not the order of all files or AST IDs in a reordered project.

Significant cause parameters also include expanded operands of invalid `#line`/`#include` directives and conditional expressions, and previous/new replacement signatures for incompatible macro redefinitions. Their physical directive position may be identical while the reasons differ. Directive failures retain macro expansion ancestry; redefinition notes use `previous-definition` and `new-definition`. Command-line definitions use the documented synthetic source-buffer ranges, not invented physical files. For two independent missing expressions inside one expansion, the physical causal origin within the definition distinguishes their reasons even when both primary ranges point to the same invocation.

For `#define ASSIGN(x) x =` followed by `ASSIGN(total);`, the missing right operand is an `ErrorExpression` with an empty recovery fragment and a zero-length primary range immediately before `;`. Related roles `macro-invocation` and `macro-definition` identify the full call and the `=` token in the definition. Recovery at this boundary preserves an independent malformed initializer in the following statement as a separate occurrence/group. For `JOIN(+, *)` with `#define JOIN(a,b) a ## b` in a header, the primary range is the invocation; `macro-argument-left`, `macro-argument-right` and `macro-paste-operator` identify `+`, `*` and `##` respectively, retaining all three physical ranges in the API and JSON. The failed expansion itself remains in its expansion chain.

Assignment recovery cannot cross an unmatched enclosing delimiter: `(x = ;`, `if (x = ;` and `f(x = ;` stop the unit with the original missing-expression error, rather than claim recovery and add derivative syntax errors. A preprocessing failure likewise remains the primary cause instead of producing a second parser error from the truncated token prefix. This bounded handling does not suppress independent errors after a valid recovery boundary.

On a damaged project, root JSON adds `diagnosticGroups`. Every group includes its key, message, all three counts and full `occurrences`; each occurrence includes the original recovery fields plus primary range, reason, related ranges and available context. Context serializes include/expansion values, not node pointers, and omits `variantId` when null. File `parseProblems` and `ErrorExpression.problem` use the same occurrence schema. Healthy snapshots omit the root error fields, so their existing golden JSON is unchanged.

The CLI prints one `error [code]` at each group's primary position, followed by the translation-unit/variant/occurrence counts and distinct, deterministically ordered `note [role]` lines for related ranges. Grouping does not discard occurrence evidence or merge independent errors after recovery. Current grouping covers preprocessing/parsing `ParseProblem` values, not all semantic-analysis diagnostics or a general dependency/suppression engine. Existing status 1, diagnostic exports and source-generation guards remain unchanged. [Fixture ranges and commands](../src/test/resources/cpp03/diagnostic-provenance/README.md) and `DiagnosticProvenanceIT` verify API/CLI JSON equality, exact coordinates, reverse traversal and snapshot preservation.

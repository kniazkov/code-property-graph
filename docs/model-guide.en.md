<!-- Code Property Graph: English model and analysis reference. MIT License. -->
# Program graph model and analysis

[Русская версия](model-guide.ru.md) · [README](../README.md)

This guide describes the public API and input grammar implemented as of 5 November 2026. The examples use Java 21. When adding a construct, update both editions, the coverage table at the end and the corresponding parser or CLI test.

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
| `GREEN` | Recognized console output becomes a `PrintStatement` after resolution. C++ written types gain profile-dependent semantic descriptors without losing their spelling. Flow edges and warnings describe the transformed tree. |
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
| Top level | `class C { … }`, `public class C { … }` | `class`/`struct`, functions, variables, `typedef`, namespaces, namespace aliases, `using`, enums and linkage specifications (see #23/#24) |
| Class method | `public static void main(String[] args)` | `static void main()` |
| Local declarations | `int x = expression;` | Fundamental/named types, recursive declarators, comma-separated declarations, storage specifiers, `typedef`, enums and nested types (see #23/#24) |
| Literals and arithmetic | Decimal signed 32-bit integers, grouping, unary and binary `+`, `-`, binary `*`, `/` | Decimal/octal/hex integers and `U`/`L`/`UL` suffixes with signed-32-bit payload; the same arithmetic plus `<<` |
| Assignment and comparison | `=` (including chains), `== != < <= > >=` | Same; type/expression `sizeof` with conservative target-dependent facts (see #23) |
| Access and calls | `obj.member`, `f(a, b)`, `C.main(args)` | `obj.member`, `f(a, b)`, `C::main()`, address-of `&x`, dereference `*p` |
| Control | Blocks, `if`, `if/else`, `while`, `return;`, `return expr;` | Same |
| Comments and directives | `//`, `/* … */` | Both comment forms; includes, macros and conditional directives (see #15–#19 below) |
| Console normalization | `System.out.println(x)` | `std::cout << x << std::endl` |
| Damaged input | Strict failure | Strict `Cpg.read`; opt-in diagnostic recovery with `ErrorExpression` (see #21) |

The Java main parameter name can vary. Coverage describes syntax recognition; full compiler type and semantic checks are outside its scope. Configured C++ headers are expanded; preprocessing coverage is described separately below and does not expand the syntax grammar. Recursive function, pointer and array declarators are described in #23. Constructors, templates, general initialization, array subscripting, strings, `for`, `break` and `continue` remain outside the current input grammar; recognizing a declaration does not implement all operations on its type.

**Model nodes available ahead of the parser** include remainder `%`, right shift `>>`, logical negation `!` and pointer member access `p->field`. C, Python, JavaScript and Ascend C have no input parser yet. Cross-language generation covers small Java/C++ programs with the supported main and console forms. See the README for translation constraints.

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
These are input records rather than new syntax nodes. Configured header
processing and macro expansion are described below; #23 adds a target platform
to each translation unit.

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

`CallExpression.candidates()` exposes possible targets, `resolution()` distinguishes unresolved/resolved/ambiguous/ODR-conflict, and compatible `target()` is present only for one resolved target. Local qualified members prefer their own TU, so repeated in-class definitions from a common header do not create false alternatives. Unknown composition exposes all matching cross-TU free-function definitions when no local definition wins. Known composition restricts lookup to selected units and diagnoses competing free-function definitions; duplicate definitions inside one TU are conflicts even without a named build. The scoped linkage work in [#24](#scopes-declarations-and-linkage-24) adds declaration identities, internal/external linkage and C language linkage. Full overload selection and class/template ODR remain later tasks.

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

Since #23, implementation-defined choices come from the effective [platform profile](#platform-profiles-and-recursive-declarators-23), never from the host compiler. Preprocessing signed/unsigned arithmetic uses the profile’s `long` width (64 bits by default); unsigned arithmetic wraps, signed division truncates toward zero, signed right shift extends the sign, and left shift retains the target-width low bits. Evaluated signed arithmetic overflow, division by zero and shift counts outside that width are diagnosed. Ordinary character constants pack bytes from `executionEncoding` into target-byte slots; numeric escapes retain the low target byte. Each packed execution octet is nonnegative independently of plain `char` signedness; the complete packed value retains the low `long` bits. Wide character constants use `wcharType` signedness and width, and the final UTF-16 code unit or UTF-32 scalar selected by `wideExecutionEncoding`. Multicharacter packing remains an explicit implementation choice, not a promise to match every native compiler. Full literal and conversion semantics for ordinary expressions remain in #25.

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

## Platform profiles and recursive declarators (#23)

### Target settings and units

`com.kniazkov.cpg.build.PlatformProfile` is an immutable target description, independent of the machine running Java or the native test compiler. `PlatformProfile.defaults()` and `builder()` use the following complete default table. **Both columns are in bits**; a target byte has `bitsPerByte = 8`, and `sizeof` returns a count of target bytes.

| Storage category | Size, bits | Alignment, bits |
|---|---:|---:|
| `bool` | 8 | 8 |
| `char`, `signed char`, `unsigned char` | 8 | 8 |
| `short`, `unsigned short` | 16 | 16 |
| `int`, `unsigned int` | 32 | 32 |
| `long`, `unsigned long` | 64 | 64 |
| `long long`, `unsigned long long` (profile storage only) | 64 | 64 |
| `wchar_t` | 32 | 32 |
| `float` | 32 | 32 |
| `double` | 64 | 64 |
| `long double` | 128 | 128 |
| Ordinary pointer | 64 | 64 |

Signed and unsigned variants share storage settings, not type identity. The default display name is `default-lp64`. Plain `char` is signed by default; `wcharType` is `int`, `sizeType` is `unsigned long`, source and ordinary execution encodings are `UTF-8`, and the wide execution encoding is `UTF-32`. The integer range model is two’s complement without padding, with `bool` restricted to 0..1. `integerRange(type)` returns arbitrary-precision limits; `sizeBits`, `sizeBytes` and `alignmentBits` expose storage facts. The `long long` entry does **not** enable C++11 type syntax or `LL` literals in C++03. No floating-point arithmetic format or member-pointer ABI is inferred from this table.

For example, save this partial override as `long32.json`:

```json
{
  "inherits": "default",
  "name": "long32",
  "sizes": {"long": 32},
  "alignments": {"long": 32}
}
```

`PlatformProfile.read(path)` loads this JSON, and `fromSettings(map)` accepts the same schema. `inherits` is required and must be `"default"` or `"none"`. With `"default"`, omitted settings inherit the documented defaults; an explicit `"alignments": {"long": null}` instead makes long alignment unknown. Reducing the long size without changing its inherited 64-bit alignment is invalid. With `"none"`, supply `bitsPerByte`, every scalar `sizes` entry, `charSigned`, `wcharType`, `sizeType`, all three encoding settings and `aggregateLayout`; `name` is optional and omitted alignments are unknown. The storage keys are `bool`, `char`, `short`, `int`, `long`, `long long`, `wchar_t`, `float`, `double`, `long double`, `pointer`. `toSettings()` emits the complete effective profile with `inherits: "none"` and explicit nulls for unknown alignments, suitable for round-tripping.

`bitsPerByte` is between 8 and 4096; each scalar size is at most 4096 bits. Sizes and known alignments must be positive whole target bytes; each alignment divides its size and is a power of two target bytes. Validation also checks integer/floating width ordering, the underlying `wcharType` width, wide-encoding capacity, valid encodings and supported `sizeType` (`unsigned int` or `unsigned long`). `sourceEncoding` accepts an available Java charset; `executionEncoding` is restricted to `UTF-8`, `ISO-8859-1` or `US-ASCII`, and `wideExecutionEncoding` to `UTF-16` or `UTF-32`. `wcharType` must be a C++03 integer type, excluding `bool`, `wchar_t` itself and `long long`. Invalid or unknown settings are configuration errors, not guessed host defaults. A 16-bit target byte is supported when all affected sizes/alignments are consistent; `long = 64` then means `sizeof(long) = 4`, not 8.

### Selecting a profile

```java
import com.kniazkov.cpg.build.PlatformProfile;

PlatformProfile long32 = PlatformProfile.builder().name("long32")
        .sizeBits("long", 32).alignmentBits("long", 32).build();
Cpg.Options options = new Cpg.Options(Language.CPP, Cpg.Stage.FOLDED)
        .withPlatform(long32);
Project project = Cpg.read(Path.of("src/test/resources/cpp03/platform-declarators/sources"), options);
```

`Build.TranslationUnit.platform()` stores the effective profile; `unit.withPlatform(profile)` selects one per translation unit. Existing constructors retain the default profile. An optional compilation-database entry field `"platformProfile": {...}` accepts an inline settings object using the same schema, not a filename. `Options.withPlatform(profile)` or CLI `--platform-profile FILE` overrides every imported unit; without an override, per-entry settings win over defaults. These settings apply to C++ input only.

```sh
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --stage folded \
  --input src/test/resources/cpp03/platform-declarators/sources \
  --platform-profile src/test/resources/cpp03/platform-declarators/long32.json \
  --json target/platform-long32.json
```

The profile is retained through preprocessing, snapshot edits and source-generation replay. `stableIdentity()` includes effective semantic settings but excludes the display name; `TranslationUnit.variantId()` includes that identity. Changing only the name creates no new variant, while changing widths, alignments, encodings or layout does. Two profiles for one physical `.cpp` can therefore coexist in a project. This is configuration identity, not a claim that the host compiler uses either target ABI. Exported build units carry the complete effective `platform` object (the database input field is named `platformProfile`). `externalInputs` owns a `platform-profile` root with its predefined macro children and each configuration’s `platform-selection` entry, including folder input without a build object.

Source files and headers are decoded with `sourceEncoding`. For C++→C++ output, the packaged CLI encodes generated roots and headers with their captured effective source encoding. Conflicting encodings for one physical file or text unrepresentable in that encoding fail before any requested export is written. `Cpg.sources` returns encoding-neutral Java strings; Java/cross-language CLI output remains UTF-8.

Predefined target facts include `__CHAR_BIT__`, `__SIZEOF_SHORT__`, `__SIZEOF_INT__`, `__SIZEOF_LONG__`, `__SIZEOF_LONG_LONG__`, `__SIZEOF_FLOAT__`, `__SIZEOF_DOUBLE__`, `__SIZEOF_LONG_DOUBLE__`, `__SIZEOF_POINTER__`, `__SIZEOF_WCHAR_T__`, `__SCHAR_MAX__`, `__SHRT_MAX__`, `__INT_MAX__`, `__LONG_MAX__`, `__WCHAR_TYPE__`, `__WCHAR_MAX__` and `__SIZE_TYPE__`. Unsigned plain char/wchar add `__CHAR_UNSIGNED__`/`__WCHAR_UNSIGNED__`. These facts are reserved against source/command redefinition or undefinition; no compiler or OS identity macros are invented. The profile also controls [preprocessing expression choices](#conditional-compilation-catalog-and-external-inputs-19).

### Written types and structural identity

The parser builds nested `NamedType`, `QualifiedType`, `PointerType`, `ReferenceType`, `ArrayType`, `FunctionType` and `MemberPointerType` nodes. `NamedType.fundamentalKind()` classifies fundamental spellings. `TypedefDeclaration.type()` owns the aliased syntax; a use retains its written `NamedType.name()` and its `alias()` reference. For `typedef int Count; Count value;`, the written type is `Count`, while `canonicalIdentity()` resolves the alias to `int`.

Declarators are composed from the declared name outward, including parentheses, abstract parameter/type-id declarators, comma-separated declarations, cv levels, function parameters and variadic function types. Consequently `int *a[3]` is an array of pointers, `int (*a)[3]` a pointer to an array, and `int (*f)(double)` a pointer to a function. `int C::*p` has a separate owner type and pointee; a reference is not an ordinary pointer. `ArrayType.bound()` owns an optional expression rather than requiring an integer length. Builders, Visitor dispatch, immutable copying, JSON and C++ generation retain these structures. Typedef recognition follows the scopes described in #24 below. Function-pointer aliases and declarations through function typedefs are supported; the latter retain their written alias in `CallableDeclaration.writtenType()`. Simple class/struct bodies still support a single access section; default arguments, general direct initialization, mixed access sections and full declaration validity remain deferred. Storage specifiers and nested declarations are described in #24.

`TypeNode.canonicalType()` follows outer typedef aliases; `canonicalIdentity()` recursively records the structure and qualifiers, with function-parameter array/function adjustment and top-level parameter cv handling. Target width is not part of language identity: 32-bit `int` and 32-bit `long` remain different. Portable integer bound arithmetic whose unsuffixed literals and intermediate results fit −32767..32767 is canonicalized, so `int[1+2]` and `int[3]` share an identity; negative division/remainder and target-dependent bounds stay symbolic. This is a structural identity for the implemented syntax, not full C++ type equivalence or overload resolution: unresolved named types and remaining symbolic bound spellings still need later semantic work. Declaration/scope/linkage resolution is described in [#24](#scopes-declarations-and-linkage-24); overloads, ADL and complete source-type identity remain #30.

### Green facts, sizeof and conservative folding

`TypeSemantics.profile(file)` retrieves the owning translation unit’s profile. `describe(type, profile)` returns a `TypeRepresentation` with `canonicalIdentity`, `profileIdentity`, optional `sizeBits`, `sizeBytes`, `alignmentBits` and `integerRange`. `TypeNormalizer.green(project)` attaches it as `TypeNode.semanticType()` without replacing the written nodes or typedef links. `GREEN` and `FOLDED` run this pass; an unchanged edit preserves the facts, while an edit changing the canonical identity invalidates stale facts. Re-run normalization after changing the type or profile. JSON represents typedef references with `aliasId`, recursive types with their owned children and `canonicalIdentity`, and green facts in `semanticType`; unknown fact fields are omitted. `integerRange` uses `minimum`/`maximum`. This omission differs from explicit null alignment entries in an effective profile.

`TypeSemantics.sizeof(type, profile)` and `sizeof(sizeOfExpression, profile)` return `Optional<BigInteger>` in **target bytes**. Known cases include fundamental object types, ordinary pointers, references to a known referent, and arrays with a known element size and positive constant bound. Restricted expression typing handles resolved variables/parameters and other supported expressions; a direct array operand does not decay to a pointer, while an array/function parameter has the adjusted pointer type. `SizeofExpression` owns exactly one type or expression operand. The expression is unevaluated; its syntax is not a runtime data read or write.

`TypeSemantics.layout(fields, profile)` is a separate helper for a supplied ordered list of ordinary non-static object fields. With `aggregateLayout: "sequential"`, each field starts at its alignment, aggregate alignment is the maximum field alignment, and total size includes tail padding to that alignment (minimum one target byte for an empty list). For `char, int`, the default offsets are 0 and 4 target bytes, size 8 and aggregate alignment 32 bits. Overriding int alignment to 8 bits gives offsets 0 and 1, size 5 and alignment 8 bits. `aggregateLayout: "unknown"`, any unknown required size/alignment, or a reference field returns no layout. An unknown alignment alone does not erase a known scalar `sizeof`.

This helper does not derive a complete class layout from `struct` syntax. `void`, function types, incomplete/unresolved classes, member pointers and unmodelled class/base/vtable layouts have no justified size. Unknown valid `sizeof` expressions and symbolic bounds remain unfolded. #24 diagnoses `sizeof` of a class that is incomplete at the use; the remaining invalid-`sizeof` diagnostic work stays in #28. In particular, a structural field list is not a promise of a native C++ ABI.

The ordinary C++ parser recognizes decimal, octal and hexadecimal integers with `U`, `L` and combined suffixes, choosing C++03 candidate types from the target range. Values outside the signed-32-bit node payload are still diagnosed; complete literal values and written-form preservation remain in #25.

C++ constant folding uses the effective integer ranges, promotions and signed/unsigned arithmetic for the supported integer `+`, `-`, `*`, `/` and model-level `%` expressions and unary `+`/`-`, including known `sizeof` values in array bounds. Unsigned results wrap at target width; signed overflow and division by zero prevent folding instead of inventing a value. Internal arithmetic uses `BigInteger`, but published `IntegerLiteral.value()` remains bounded to signed 32-bit storage: larger proven results remain expressions. Synthesized non-int constants retain `cppType` and the corresponding C++ suffix. Full source literal forms, expression conversions and operator coverage are #25; arbitrary symbolic object layout and the remaining invalid-`sizeof` diagnostics belong to #28. Java’s existing folding and translation tests remain unchanged.

The [platform-declarators fixture](../src/test/resources/cpp03/platform-declarators/README.md) and [E2E contract](e2e.en.md#platform-profiles-and-declarators-23) exercise these boundaries through the public API and packaged CLI. Native execution validates the default fixture only; custom-profile facts are compared through API/CLI rather than asserting a foreign ABI on the host compiler.

## Scopes, declarations and linkage (#24)

### Source occurrences and translation-unit ownership

A declaration remains a source occurrence, rather than being replaced by one project-wide symbol. `Declaration.isDefinition()` distinguishes a definition from a declaration; `previousDeclaration()` and `definition()` are optional, non-owning links. `linkage()` returns `NONE`, `INTERNAL` or `EXTERNAL`, while `languageLinkage()` records `C++` or `C`. These are different properties: language linkage does not make an internally linked entity visible in another translation unit.

`NamespaceDeclaration.members()` owns the declarations of one namespace opening, and `anonymous()` distinguishes an unnamed opening. Reopening a namespace retains separate source nodes while sharing lookup in that namespace. `NamespaceAliasDeclaration` retains `name()` and `targetName()` with an optional namespace `target()`. `UsingDirective` retains `namespaceName()` and its namespace `target()`; `UsingDeclaration` retains `qualifiedName()` and the declaration it introduces as `target()`. These reference targets are not syntax children. Ordinary Visitor traversal visits each source declaration once, not once per lookup that uses it.

`EnumDeclaration.enumerators()` owns source-ordered `EnumeratorDeclaration` nodes; each enumerator has `name()` and an optional written `value()` expression. This is the unscoped C++03 enum syntax, not C++11 scoped enums. `VariableDeclaration.modifiers()` retains written storage specifiers; `ParameterDeclaration.modifiers()` retains permitted parameter `register`. Class/struct forward declarations use the existing `ClassDeclaration` with `isDefinition() == false`, and nested types remain owned by their class. Declaration names, source ranges and preprocessing provenance stay available on the original occurrences.

Two `.cpp` files including the same physical header have independent declaration nodes, even when those nodes have exactly the same `SourceRange`. The [scopes-linkage fixture](../src/test/resources/cpp03/scopes-linkage/README.md) includes one `typedef VALUE_TYPE Width;` line from `config.h` under two commands: `-DWIDE` makes `VALUE_TYPE` expand to `long` in `a.cpp`, while `b.cpp` gets `int`. One `Project` retains both typedefs and each `Width` use points into its own translation unit. Physical range alone is therefore not an entity key; preserve the owning `SourceFile` and `variantId()` when identifying a use or declaration.

### Written type lookup and completeness

For a `NamedType`, `name()` is the written spelling and optional `elaboratedKeyword()` preserves a written `class`/`struct`/`union`/`enum` prefix; `declaration()` is the declaration found at the use, `alias()` is the immediate typedef when applicable, and `resolutionSteps()` describes the lookup path. Each immutable `TypeResolutionStep` has `kind()`, `via()` and `target()`. Both node fields are non-owning references to declarations already owned by this snapshot. The path explains lookup; it does not rewrite the type spelling.

```cpp
namespace shop {
    typedef long RawId;
    typedef RawId OrderId;
    typedef int Count;
}
using namespace shop;
OrderId id;
Count count;
shop::OrderId qualified;
```

The unqualified `OrderId` and `Count` uses have distinct resolution steps that refer to the **same** `UsingDirective`, with their respective found declarations as targets. The directive itself targets `shop`, not either typedef. The explicitly qualified `shop::OrderId` does not acquire a spurious using-directive step. Following `alias().type()` exposes each typedef spelling in sequence; `canonicalType()` follows the outer alias chain and `canonicalIdentity()` yields `long` for `OrderId`. Generated C++ retains `OrderId id;`, rather than replacing the written name with `long`.

```java
for (TypeResolutionStep step : written.resolutionSteps()) {
    System.out.println(step.kind() + ": " + step.via().getClass().getSimpleName()
            + " -> " + step.target().getClass().getSimpleName());
}
TypeNode current = written;
while (current instanceof NamedType name && name.alias().isPresent()) {
    current = name.alias().orElseThrow().type();
}
System.out.println(current.canonicalIdentity());
```

`completeAtUse()` is a source-point fact, not a query asking whether a definition exists anywhere in the finished project. In `class Order; Order *current; class Order { int id; };`, the pointer's named pointee links to the visible forward declaration; that declaration's `definition()` links to the later class definition. Its `completeAtUse()` remains false. The pointer is permitted, but the later definition cannot retroactively make `sizeof(Order)` before the definition valid. Completeness does not establish class size or ABI: even a complete class need not have a known `TypeSemantics.sizeof` result (#28).

A first elaborated introduction such as `struct New *p;` owns an implicit forward `ClassDeclaration` (`implicit() == true`, JSON `implicit: true`). The written `NamedType.elaboratedKeyword()` introduces it during generation; no extra standalone declaration is emitted. An edited introduction whose spelling contradicts its implicit declaration is rejected rather than emitting a different entity. Qualified variable/enumerator uses expose `MemberExpression.target()` as an optional non-owning declaration link, serialized as `targetId`.

### Source-point lookup, linkage and diagnostics

Inline member-function bodies are parsed with the completed enclosing class type-name environment, including later typedefs. Other uses retain their actual source-point visibility. Class and enum completeness begins at the closing brace, with the complete-class context exception for inline member-function bodies.

The parser's type-name environment follows global, namespace, class, function-parameter and block scopes, including shadowing and namespace reopening. It uses that environment to distinguish `T(a);`: a visible type `T` makes this a declaration of `a`, while a visible ordinary callable `T` makes it a call expression. With visible types `T` and `U`, `T x(U());` is a function declaration with a function-typed parameter, not direct initialization. Name resolution publishes the corresponding declaration references after parsing. A variable is introduced before its initializer, so the inner `x` in `int x = 1; { int x = x; }` binds to the inner declaration.

File-scope `static` and anonymous-namespace entities remain translation-unit-local. Block-scope function and `extern` variable redeclarations inherit linkage from a visible earlier declaration, including internal or C language linkage. An inherited C linkage does not introduce a written linkage specification inside the block. External functions are matched by qualified name, language linkage and the implemented parameter-type identity. A visible local declaration is required for ordinary C++ lookup; discovering an unrelated definition in another file does not make an undeclared name visible. `extern "C"` uses C entity matching, while preserving the written linkage specification. Repeated declarations are distinct occurrences connected through `previousDeclaration()` and, when unambiguous, `definition()`. An unknown build composition exposes possible external definitions through `CallExpression.candidates()` without declaring a cross-unit ODR failure; a selected `Build.Composition` checks the chosen definitions together. Multiple definitions within one unit are errors in either mode. Matching inline definitions from different units are handled conservatively by comparing their generated syntax; this is not full token/lookup-equivalent class, template or inline ODR validation.

`CallExpression.resolution()` retains `UNRESOLVED`, `RESOLVED`, `AMBIGUOUS` and `ODR_CONFLICT`. `target()` is present only for one resolved callable. Distinct overloads remain candidates; #24 does not rank conversions or select a best overload (#30). Resolved class/enum declarations expose optional `Declaration.typeIdentity()`: a qualified identity, with translation-unit context for local or anonymous-namespace types. `NamedType.canonicalIdentity()` uses it rather than conflating unrelated same-spelled classes in signatures. This extends #23 structural identity without claiming complete C++ type equivalence or overload selection.

The resolver attaches semantic `Diagnostic.Severity.ERROR` findings with stable codes:

- `incomplete-type`: `sizeof` needs a complete type at that source position, including a typedef naming an incomplete class.
- `conflicting-declaration`: incompatible declarations of the implemented variable, typedef or callable entity, including incompatible C signatures.
- `odr-conflict`: multiple non-permitted definitions within a translation unit or selected composition.

These findings are `Node.diagnostics()`, not `ParseProblem` records or #22 grouped parser diagnostics. `Project.hasErrors()` and `SourceFile.hasErrors()` still report **parse** errors only; inspect diagnostic severities as well when checking semantic validity. Strict `Cpg.read`/`readCompilationDatabase` still return the analyzed graph when parsing succeeds but resolution finds a semantic error. `PARSED` has not run these checks. The CLI prints the semantic error, exits with status 1, writes separately requested JSON/SVG, and does not create source output. `Cpg.sources` and direct source rendering also refuse a snapshot containing an attached semantic ERROR. An unknown size or unresolved name alone is not an error finding or proof of language validity.

### JSON, generation and edits

JSON preserves the owned declarations and adds optional reference fields rather than duplicating target nodes:

- Namespace nodes contain `name`, `anonymous`, `members`; aliases contain `name`, `targetName` and resolved `targetId`.
- Using directives contain `namespaceName` and resolved namespace `targetId`; using-declarations contain `qualifiedName` and resolved declaration `targetId`.
- Enums contain `name`, `enumerators`; enumerators contain `name` and optional owned `value`. Variables and parameters contain `modifiers` when nonempty.
- Declaration metadata uses lowercase `linkage` when not `none`, `languageLinkage` when not `C++`, `isDefinition: false` for non-definitions, optional `previousDeclarationId` / `definitionId`, and optional class/enum `typeIdentity`. Omitted metadata has the API defaults `NONE`, `C++` and true, respectively; omitted reference fields mean no link.
- A function declared through a typedef contains owned `writtenType` in addition to its callable `returnType` and `parameters`. For `typedef int F(double); F f;`, this retains the spelling `F f;` and a usable signature. A function definition through such a typedef is rejected as invalid C++03.
- A named type preserves optional `elaboratedKeyword`; a resolved type contains `declarationId` and `completeAtUse`. Typedef uses retain `aliasId` and `canonicalIdentity`. A nonempty `resolutionSteps` array contains `{kind, viaId, targetId}` entries. Current step kinds are `using-directive`, `using-declaration`, `namespace-alias`, `typedef` and `declaration`. A terminal typedef to a fundamental type points to itself in the step because fundamental type syntax is not a declaration; follow its owned `type` to inspect the final type.

The same references also appear as generic `ref` edges. IDs refer to nodes in this JSON snapshot; they are not project-wide symbol IDs or physical-range identities. DOT/SVG labels the written-type lookup paths and previous/definition relations. `completeAtUse` is meaningful with a resolved declaration; an unbound name must not be treated as a proven complete type merely from the API's default true value.

Canonical C++ generation emits namespaces, aliases, using declarations/directives, enums, forward declarations and storage modifiers. It may write individual `extern "C" { ... }` blocks rather than preserve the original grouping; the declaration/definition distinction and storage semantics are retained. The preserved-source path retains unchanged captured files and headers and still applies the #20 multi-configuration reconciliation and reparse checks. These constructs do not gain Java translation: the portability guard rejects unsupported cross-language output rather than silently erasing them.

`edit().build()` copies owned declarations and all reference targets into the **new** snapshot, preserving shared directive identity, type chains and forward/definition cycles. It does not recompute lookup. Editing drops derived diagnostics and flows; after changing names, declarations, order or types, call `NameResolver.resolve(edited)` and then the needed normalization/flow passes before interpreting or exporting the result. Query the returned snapshot, not old nodes. `ProjectEditor` validates that staged relation targets belong to its source project, redirects replacement edges on build, and refuses removal of a declaration that still has incoming references. Reanalysis recalculates bindings and clears stale lookup/import links without mutating the old snapshot.

Remaining boundaries include full overload selection and ADL (#30), complete class ABI/size (#28), default arguments and general initialization, mixed class access sections, a class/enum definition followed by an object declarator in the same statement, and full enum value/range semantics. Scope and declaration support does not imply constructors, inheritance, access checking, templates or complete C++03 declaration validity. The [E2E contract](e2e.en.md#scopes-declarations-and-linkage-24) separates portable native execution from API-only lookup and negative diagnostic checks.

## Literals, expressions and standard conversions (#25)

### Literal values and spelling

C++03 literal nodes keep source spelling separately from their value and type. `IntegerLiteral.bigValue()` is exact `BigInteger`; the compatibility accessor `value()` returns an `int` only when representable and otherwise throws `ArithmeticException`. `cppType()` records the target-profile choice among the C++03 integer candidates, including decimal/octal/hexadecimal spelling and `U`/`L` suffix combinations. Equal widths do not merge `int` and `long`. Values outside the target's candidate ranges are rejected, as are post-C++03 binary literals and `LL` suffixes.

`BooleanLiteral` stores a boolean. `FloatingLiteral.value()` is the lexical decimal `BigDecimal`, with `cppType()` distinguishing `float`, `double` and `long double` and `spelling()` retaining the suffix/exponent. This is **not** a target-rounded floating-point value or a complete target floating-point evaluator. Exponents outside the supported decimal range are diagnosed.

`CharacterLiteral` stores an exact integer value, `wide`, `cppType`, `spelling`, `codeUnitBits` and `valueBits`. Numeric escapes denote code units; source characters and universal character names use the profile's ordinary/wide execution encoding. The implementation-defined multicharacter convention packs ordinary execution units most-significant-first into the target `int`, truncates to its width and applies its signedness; a wide multicharacter literal retains the last unit. These are explicit model choices, not portable native execution expectations.

`StringLiteral.codeUnits()` contains decoded units **without** the terminating zero. Its type is a const `char`/`wchar_t` array whose bound includes that zero. Adjacent tokens form one node; if any token is wide, decoding uses the wide encoding. `spelling()` joins their token spellings with spaces, rather than promising byte-for-byte preservation of intervening comments/whitespace. Captured-source generation retains unchanged physical input independently.

### Operators, type and category

The parser and generator represent precedence from postfix/index/member operations through unary operators, pointer-to-member `.*`/`->*`, arithmetic, shifts, comparisons, bitwise/logical operators, `?:`, assignment/compound assignment and comma. Prefix/postfix increment and decrement have distinct nodes. `ConditionalExpression` owns condition/then/else operands; `CompoundAssignmentExpression` owns target/value and retains its operator. Parenthesization is checked by generation and reparsing, including the special grammar of conditional and comma operands. Logical operators retain short-circuit syntax; adding nodes does not claim complete CFG/DFG or constant folding for every operator.

At resolved stages, `Expression.semanticInfo()` exposes immutable `ExpressionInfo`: optional canonical `resultType()` and `category()` (`LVALUE`, `RVALUE`, `UNKNOWN`). The type identity is a string value, not an owned type or declaration edge. It is distinct from a computed constant value. Missing result type/unknown category must not be interpreted as successful type checking. `TypeSemantics` derives built-in arithmetic types, promotions, pointer/index/member-pointer results and lvalue-sensitive assignment/comma/conditional results under the effective profile.

`NameResolver.resolve` applies `StandardConversions.analyze` after name/call resolution. The conservative built-in pass inserts conversions for supported initializers, assignments, uniquely resolved call arguments (including variadic promotions), unary/arithmetic/logical operands and conditional expressions. It represents lvalue-to-rvalue, array/function decay, integral/floating promotions, arithmetic conversion, pointer qualification, integer-zero null-pointer conversion and supported reference binding. Conversion construction is limited to representable fundamental/cv/pointer/reference/function types; it is not overload ranking, user-defined conversion, arbitrary class conversion or a complete semantic-validity checker. Enum promotions and a guessed `ptrdiff_t` for pointer subtraction are not provided.

### Explicit syntax, implicit nodes and exports

`ConversionExpression` owns `operand()` and `targetType()`, and records `kind()`, `isExplicit()` and optional canonical `sourceType()`. An explicit `static_cast<long>(x)` has kind `STATIC_CAST` and the written cast's source range. For a unique `f(long)` target, `f(x)` with `int x` instead receives implicit wrappers. Both expose source/target types, while only the explicit node emits `static_cast`; implicit wrappers emit their operand transparently. Thus analysis does not introduce written casts that might affect later overload selection. Other cast families, `new`/`delete`, class layout and full typed folding remain #28; default arguments remain #26, and overload/ADL/user-defined conversion work remains #30.

JSON adds `semanticInfo: {resultType?, category}` to analyzed expressions. Literal records carry their values/spellings/types; large integers remain exact JSON numbers, so consumers must avoid lossy binary-double parsing. Strings export `codeUnits`. Conversion records contain owned `operand`/`targetType`, `explicit`, `conversionKind` and optional `sourceType`. DOT/SVG includes literal and conversion labels. Copying retains facts and owned conversion types in the new snapshot; after a semantic edit rerun resolution rather than trusting copied analysis. C++-specific literals/operators/conversions do not silently acquire Java translations: unsupported cross-language output is rejected, while existing Java behavior remains covered by regressions.

The [E2E contract](e2e.en.md#literals-expressions-and-conversions-25) separates profile/model checks from portable native execution.

## Functions and default arguments (#26)

Default-argument name restrictions follow the checked-in C++03/N1638 baseline: parameters (including the current parameter) and local variables are rejected even inside `sizeof`. The later CWG 2082 relaxation is not adopted; modern compilers may accept these cases in `-std=c++03` mode. The checked-in text is explicit in 3.3.1/1, 3.4.1/11 and 8.3.6/7,9.


`ParameterDeclaration.defaultValue()` is an optional **owned** expression, present only where the default is written. The parameter's source range and provenance include that expression. Function declarations and definitions retain their existing return/parameter types, variadic marker, inline modifier and reference/array declarators. Defaults are not part of a function type or signature.

`CallExpression.arguments()` contains only written arguments. A resolved call exposes omitted arguments through `defaultArguments()`: each `DefaultArgument` records the zero-based `parameterIndex()`, the visible supplying `declaration()` and its `parameter()`. `expression()` returns that parameter's shared default expression. These are non-owning references, not cloned syntax or eagerly evaluated arguments. Thus the two omitted calls in `void log(int level = next()); log(); log(2); log();` reference one owned `next()` expression, while the middle call owns its explicit argument. At execution, each omitted call evaluates the default independently.

JSON nests `defaultValue` under its parameter and exports nonempty call `defaultArguments` as `parameterIndex`, `declarationId`, `parameterId` and `expressionId`. DOT displays labelled declaration/parameter/expression edges. Snapshot copying redirects every link into the copied graph. C++ generation emits defaults only at their written parameter declarations and leaves omitted calls omitted; preserved-source edits retain unrelated comments and macro invocations. Java generation rejects C++ defaults and unsupported function signatures rather than silently erasing them.

Defaults follow declaration visibility, not a later definition or the dynamic target. Function-pointer calls do not inherit declaration defaults. Full overload ranking/ADL (#30), virtual dispatch and static-vs-dynamic default selection (#29), and full interprocedural effects (#42) remain separate work. See the [E2E contract](e2e.en.md#functions-and-default-arguments-26).

## Classes, initialization and lifetime (#27)

`Declaration.access()` retains each member's public/protected/private access;
`isFriend()` distinguishes friendship from ownership and storage. The original
`ClassDeclaration.memberAccess()` remains the initial/default section. Classes,
structs and unions retain ordered members, nested types and `bases()`; each
`BaseSpecifier` owns its written type and records access and `isVirtual()`.
`VariableDeclaration.bitWidth()` owns a named or unnamed bit-field width. Static
and mutable fields retain their modifiers. Qualified out-of-class member names
remain written names while resolution connects them to class declarations.

`CallableDeclaration.kind()` distinguishes ordinary functions, constructors and
destructors. Constructors/destructors have no invented return type. Constructor
`initializers()` own ordered `MemberInitializer` nodes (name and arguments) in
**written order**. `MemberInitializer.target()` is a non-owning link to the field or written base type/typedef declaration; JSON exports `targetId`. Resolution excludes constructor parameters from initializer-id lookup and distinguishes aliases and a field hiding a base name. This order is not execution order. Copy constructors and
`operator=` retain their actual source declarations and observable bodies.

`InitializationExpression` represents `DIRECT` parentheses, `AGGREGATE` braces,
and typed `TEMPORARY` construction. A normal `= expression` remains the variable's
owned initializer, preserving copy initialization. Empty typed construction is
value initialization; an absent initializer is default initialization, with a
separate zero-initialization phase for defined static-storage objects. `T x()`
remains a function declaration and C++11 `T x{...}` is rejected. Source generation
preserves these distinctions instead of introducing calls or braces that could
change overload selection.

`ClassSemantics` computes immutable, non-owning facts from the current snapshot:

- `specialMembers(class)` reports default constructor, copy constructor, copy
  assignment and destructor as `IMPLICIT`, `USER_DECLARED` or `SUPPRESSED`, with
  original declaration references. A user constructor suppresses implicit default
  construction, not implicit copy/assignment/destruction. Implicit members are
  descriptors, not fabricated source nodes. This API does not assert that an
  implicit definition is usable for every member type.
- `aggregate(class)` checks the represented C++03 class/union aggregate conditions.
- `constructionOrder(class, constructor)` pairs direct non-virtual bases and
  non-static fields in declaration order with their written initializer, if any.
  `destructionOrder` reverses that subobject order; destructor bodies execute first.
  Transitive virtual bases, unresolved bases and incomplete hierarchies are rejected by the ordering API. Their constructors receive an `unsupported-constructor-flow` warning and no partial CFG. Valid indirect virtual-base initializers are not misdiagnosed as unknown fields.
  The constructor CFG also orders supported written initializer vertices this way, and DFG
  includes their argument evaluation. Implicit calls are not cloned into the AST.
- `initialization(project, variable)` reports storage, initialization phases,
  class and written expression. `constructorCandidates` supplies source candidates
  with compatible arity, not a ranked overload result.
- `lifetimes(project)` distinguishes automatic block exit, static program exit,
  temporary full-expression end and direct reference-lifetime extension. These
  are lexical facts, not a runtime trace or an inter-TU initialization ordering.

JSON exports all written nodes, access, friend, special-member kind and implicit
member descriptors. DOT/SVG preserves ownership/reference edges; SVG retries known Graphviz ranking/triangulation failures with left-to-right straight-line routing, preserving every node, edge and label. Copies
retain syntax and redirect references; derived class facts are recalculated after
edits. Unsupported C++ class constructs are not silently translated into Java.

Inherited member lookup and virtual-base traversal/dispatch (#29), full overload ranking and access checking
(#30), ABI layout/new/delete (#28), implicit-definition validity for every class,
optional copy elision and full interprocedural lifetime effects are not claimed by
these APIs. See the [native acceptance fixture](../src/test/resources/cpp03/classes-initialization/README.md).

`FunctionFlow.complete()` is false for unsupported constructor hierarchies; its edge lists are empty, JSON exports `flowComplete: false`, and data-flow/unread-write analysis does not treat it as a complete result.

## Object sizes, casts and allocation (#28)

`SizeofExpression` and `TypeidExpression` preserve either an owned written type
or an owned expression operand. `sizeof` does not evaluate its operand. RTTI's
polymorphic-expression evaluation and runtime lookup are not replaced by invented
calls. `ConversionExpression.kind()` distinguishes `STATIC_CAST`, `DYNAMIC_CAST`,
`CONST_CAST`, `REINTERPRET_CAST` and `C_STYLE_CAST`; functional notation uses typed
`InitializationExpression` with `TEMPORARY` form. Cast validity, RTTI execution and
user-defined conversion/overload selection are not fully implemented.

`NewExpression.type()` owns the allocated type, including `ArrayType` bounds.
`placementArguments()` and `initializerArguments()` are distinct owned lists;
`hasInitializer()` distinguishes `new T` from `new T()`. `isGlobal()` and
`isParenthesizedType()` retain the written allocation form. `DeleteExpression`
owns `operand()` and records `isArray()` and `isGlobal()`. These are source
operations, not fabricated calls to allocation functions, constructors or
destructors. Allocation-function lookup, exception cleanup and runtime heap
lifetime are not asserted by this syntax model.

C++ generation retains these forms and parenthesizes operands to preserve
precedence. Java generation rejects them. JSON exports the owned operands,
`conversionKind`, allocation argument lists and form flags; DOT includes cast,
placement and array-delete labels. Snapshot copying retains all owned syntax and
redirects semantic references into the copy.

The target profile separates nominal type identity from object representation.
`fieldAlignment` (`natural`, `packed1`, `unknown`), `aggregateAlignment`
(`maximum-field`, `unknown`) and `tailPadding` (`alignment`, `unknown`) govern
supported aggregate layout. Defaults use natural field alignment, maximum field
alignment and aligned tail padding. A self-contained `inherits: none` profile
that omits these policies leaves aggregate layout unknown. `packed1` is a target
layout policy, not an injected source pragma or a claim about the host compiler.

`TypeSemantics.layout(ClassDeclaration, profile)` and `describe`/`sizeof` handle
complete supported aggregates, nested aggregates and arrays. Static fields do not
occupy object storage; empty aggregates occupy one byte. Unions, bases, virtual
members, nonaggregates, bit-fields, reference fields, unresolved or recursive
layout remain unknown. With an eight-bit byte and four-byte `int`, `struct S {
char a; int b; };` has size 8 under natural four-byte alignment and 5 under packed
one-byte alignment. Unknown policy keeps `sizeof(S)` symbolic. `ArrayType.bound()`
continues to own `sizeof(S) + 1` and its declaration reference: reanalysis with a
known profile can derive 9 without replacing the written expression or changing
the original snapshot. Changing `long` from 64 to 32 bits changes `sizeof(long)`
from 8 to 4, but never merges the overload identities of `int` and `long`.

## Inheritance and virtual methods (#29)

`BaseSpecifier` preserves each written direct base, access and `virtual` flag.
`CallableDeclaration.isPureVirtual()` records the written `= 0`; `virtual` stays
in the written modifiers and is not inserted into overriding declarations.
Out-of-class definitions retain qualified names and declaration/definition links.
`InheritanceSemantics.isVirtual(project, method)`, `overrides(project, method)`
and `overriddenBy(project, method)` derive semantic virtualness and both directions
of override relationships from the hierarchy, independently of call sites.
Same-name methods with different parameter signatures do not acquire override edges.

A call's `target()` is its statically selected declaration; `candidates()` and
`resolution()` describe static lookup. Written `arguments()` remain unchanged.
Omitted defaults remain separate `defaultArguments()` references to the visible
static declaration, parameter and owned expression. Dynamic dispatch does not
substitute defaults from a possible implementation. `isVirtualDispatch()` and
`virtualTargets()` describe possible implementations, separately from static
selection; `virtualTargetsComplete()` describes completeness of that analysis.
These are non-owning edges, not cloned syntax or a promise about the runtime
object. Re-resolve after semantic edits rather than treating copied analysis as
fresh results. Qualified calls suppress virtual dispatch.

JSON exports `pureVirtual`/semantic `virtual`, `overridesIds`/`overriddenByIds`,
`staticTargetId`, `defaultArguments`, `virtualDispatch`, `virtualTargetIds` and
`virtualTargetsComplete` as distinct fields. DOT labels static selection, defaults,
possible implementations and override directions separately. Source generation
preserves base lists, `virtual`, `= 0`, qualified definitions and omitted arguments;
Java generation refuses these C++-only forms. Copying preserves references within
the copied snapshot. The model does not fabricate object layout, ABI, vtables,
RTTI execution or a unique runtime target from a base reference. Full overload
ranking, ADL and user-defined conversions remain #30.

Construction ordering has an explicit invocation context:
`ClassSemantics.constructionOrder(owner, constructor, ConstructionContext.COMPLETE_OBJECT)`
includes virtual bases in most-derived construction order, while
`ConstructionContext.BASE_SUBOBJECT` excludes virtual bases, whose initialization
belongs to the most-derived object. The two-argument overload uses
`COMPLETE_OBJECT`; `destructionOrder` accepts the same context and reverses the
corresponding construction order. This structural order is not a fully modeled
runtime execution: callable CFGs remain incomplete for virtual hierarchies when
the constructor/destructor invocation context is unknown.

Known multiple final overriders of the same virtual base subobject produce an
`multiple-final-overriders` error even without calls. A derived override, including
a pure override, resolves that conflict; independent nonvirtual base subobjects
are checked separately. Incomplete or cyclic hierarchy facts are not guessed.
Out-of-line definitions retain in-class virtual/pure status and static defaults.
Explicit object-qualified calls suppress dispatch. Calls on the current object
inside a constructor/destructor cannot target more-derived implementations;
calls on unrelated receivers remain open-world.
Declaration/base syntax edits through ProjectEditor conservatively clear stored
virtual-call targets across the snapshot; rerun resolution to repopulate them.
Unchanged copies retain and remap these non-owning edges.

## Overloads, ADL and nominal type identity (#30)

The supported selector compares conversion sequences pairwise: exact match,
promotion, standard conversion, user-defined conversion, then ellipsis. It does
not add numeric costs across arguments: crossed better/worse arguments remain
ambiguous. Qualification/reference binding, supported derived-to-base paths,
null pointer conversions and viable default/variadic arguments participate.
An unknown conversion leaves selection unresolved. ADL adds associated class,
base-class and namespace candidates, including hidden friends and enum namespaces.
Selected inaccessible members receive diagnostics rather than silently choosing
a worse accessible overload. Non-explicit converting constructors and conversion
functions participate where their supported conversion sequence is known;
`explicit` constructors do not supply an implicit argument conversion.

Overload selection uses nominal C++ type identity, not storage width. On a
profile where `int` and `long` are both 32 bits, `f(1)` still selects `f(int)`
and `f(1L)` selects `f(long)`. Written types, typedef uses and literal suffixes
remain available to source generation; a numeric range is not a type alias.

The written operator expression remains the syntax owner. For
`Money price; Money tax; ... price + tax`, the `AddExpression` still owns its
left and right operands. `Expression.operatorTarget()` is a non-owning reference
to the selected callable; `operatorCandidates()` and `operatorResolution()`
preserve unresolved/ambiguous alternatives without inventing a unique target.
They are separate from `CallExpression.target()` and its ordinary call candidates.
Copies remap these references into the copied project. Generated C++ retains
`price + tax`, rather than rewriting it as `operator+(price, tax)`.

JSON exports `operatorResolution`, `operatorCandidateIds` and, for a selected
implementation, `operatorTargetId`. These are destination IDs, not duplicated
syntax children. DOT adds `operator implementation` or unresolved candidate
edges alongside the ordinary dashed references. Written nominal types and
`1L` remain visible in the same snapshot.

A conversion function is a `CallableDeclaration` of kind `CONVERSION`. Its
owned `conversionType()` is the written conversion-type-id, exported as
`conversionType`; `returnType()` is absent. Source generation emits, for example,
`operator int() const`, never `int operator int() const`. User-defined operators
and conversion functions are not silently translated into Java.

A selected overloaded logical operator evaluates both operands as a function
operation. Built-in `&&` and `||` keep short-circuit semantics. Analysis and
constant folding must not erase a selected user-defined implementation or treat
it as built-in arithmetic merely because its spelling is `+`, `&&` or `||`.
This contract does not claim full template overload resolution, complete ABI
behavior, or guessed semantics for unknown external/user-defined bodies.

### C++03 statements, exceptions and flow-only operations (#31)

The statement model has explicit `EmptyStatement`, `DoWhileStatement`, `ForStatement`,
`SwitchStatement`, `CaseStatement`, `BreakStatement`, `ContinueStatement`,
`GotoStatement`, `LabelStatement`, `TryStatement` and `CatchClause` nodes.
A `ForStatement` owns its initializer list, optional condition and update, and body.
A `CaseStatement` owns the following statement; an absent value denotes `default`.
Nested labels and adjacent cases retain source order rather than becoming independent
blocks. `ConditionDeclarationExpression` owns the variable declared in a condition.
`ThrowExpression.value()` is absent for a rethrow. A catch with no parameter is
`catch (...)`.

Callable and function-type exception specifications distinguish absence from an
explicit empty `throw()` using `hasExceptionSpecification()` and `exceptionTypes()`.
A callable's `functionTryBlock()` and `handlers()` preserve constructor initializer
order: `Guard() try : member() { ... } catch (...) { ... }`.
`bodyComplete()` and `BlockStatement.complete()` identify recovered, incomplete
syntax. Such syntax remains exportable but cannot be generated as source, even
when rendered independently of its source file.

CFG auxiliary vertices are not AST nodes. `AuxiliaryFlowVertex` records an owning
callable, deterministic per-flow index, kind (`EXCEPTIONAL_EXIT`, `TERMINATE_EXIT`, `IMPLICIT_CONSTRUCTOR` or
`IMPLICIT_DESTRUCTOR`), and optional non-owning object/constructor/destructor references.
JSON assigns their numeric IDs after source syntax and before the external configuration
catalogue, so API/CLI attribution does not shift flow IDs. It writes them in the callable's
`flowVertices` array with `ownerId`, `index`, `objectId`, `constructorId` or `destructorId` where
applicable. CFG endpoints refer to those IDs in the same way as syntax endpoints.
DOT uses a separate owner/index namespace and labels object/constructor/destructor references.
A missing destructor reference does not turn the operation into an explicit call
or imply a user-declared destructor exists. `flowComplete: false` identifies an
unavailable/incomplete flow; the legacy omitted flag means a complete stored flow.
Do not infer a complete flow merely because a callable has no flow metadata.

Source generation walks owned syntax only. It preserves automatic storage duration
and never inserts calls from implicit cleanup vertices. C++ exception syntax,
condition declarations and `goto` are not translated to Java. Shared empty,
loop, break/continue and switch syntax remains available where its contained
expressions and declarations belong to the supported common subset.

An implicit constructor's selected callable is exposed through `constructor()` and
`constructorId`, never through `destructor()` or `destructorId`. These references
are reconnected when copying an annotated snapshot.

The current CFG deliberately reports the affected flow as incomplete, without
publishing misleading partial edges, for these unsupported cases:

- Arrays of class objects, including partially constructed arrays.
- Explicit or implicit class prvalue temporaries requiring lifetime tracking.
- A nested throw inside an expression group whose evaluation order is unspecified.
- An unknown or disallowed escaping exception under a nonempty exception specification:
  a user-installed `unexpected` handler can change the outcome.
- Constructor/destructor paths requiring context-dependent virtual-base handling.

An escaping exception under the empty `throw()` specification reaches
`TERMINATE_EXIT`. A second exception thrown by a destructor during stack unwinding
also reaches `TERMINATE_EXIT`, rather than an ordinary catch or exceptional return.
Incomplete analysis does not itself invalidate the preserved AST or source; syntax
and semantic errors still independently block generation. A complete-flow flag is
not a claim of whole-language C++ exception/lifetime coverage.

## Physical comments and explicit removal (#32)

C++ comments are physical data, never syntax tokens. Structured Doxygen interpretation is described below. A `Comment`
records `Kind.LINE` or `Kind.BLOCK`, exact physical `text()` (including delimiters,
line splices and internal line endings), and a half-open physical `range()`.
`SourceFile.comments()` owns each physical comment once, including comments in
captured headers and inactive branches. `Node.commentReferences()` supplies
non-owning explanatory `HAS_COMMENT` links, separate from semantic `references()`.
Consecutive leading line comments can explain the following declaration; a trailing
comment on a multi-declarator statement explains its last declaration; a comment
after an argument comma explains the following argument. These links are heuristics,
not documentation interpretation. Copying/editing remaps their identities into the
new snapshot. Java parsing keeps its existing behavior and does not capture comments.

JSON owns comments in the source file's `comments` array, with `commentKind`, `text`
and normal range metadata. Explanatory edges use `kind: "HAS_COMMENT"` and destination
IDs. DOT has numbered ownership edges and dashed `HAS_COMMENT` edges. Traversal
visits each owned comment once; explanatory links never become extra syntax children.

Ordinary `Source.files`/`Cpg.sources` preserve captured C++ comments: unchanged input
is byte-identical, including inactive branches. A validated syntax edit retains
surrounding physical text; when an entire subtree must be replaced, its internal
comments are retained once before the replacement. `Source.render` is a canonical
fragment renderer, not a layout-preserving printer: it emits owned/referenced comments
once as a preamble, with their original text, for either supported target. Use file
emission when attachment position and physical layout matter. Comment-catalogue edits
are rejected by physical emission rather than silently ignored; canonical rendering
can emit a manually edited catalogue.

`com.kniazkov.cpg.utilities.Comments.remove(Project)` explicitly creates a separate
comment-free C++ project. It materializes validated AST edits, removes comments from
all captured physical sources (including inactive branches and headers), then
reparses and analyzes the result with the original build configuration and translation
time. Input snapshots and literal contents are unchanged. The overload
`Comments.remove(String file, String source)` exposes lexical cleanup of one source.
Whitespace prevents token concatenation; retained/spliced physical line endings
preserve preprocessing line semantics. The project utility requires complete captured
C++ input and rejects Java, missing provenance and invalid snapshots. It is never
called automatically by reading, analysis, generation or the CLI.

Canonical printing keeps each original comment text verbatim as a substring but
may add separating newlines. In particular an EOF `//` comment ending in `\` or
`??/` needs two newlines before subsequent syntax so phase-two splicing cannot
swallow it; its newly lexed range may therefore differ. Physical unchanged file
emission has no such relocation and remains exact. Java emission rejects comment
spellings whose C++ splicing/delimiters or Java Unicode escapes could expose commented
text as code rather than silently changing their meaning.

## Linked Doxygen entries and safe parameter rename (#33)

`Comment.documentationEntries()` owns ordered `DocumentationEntry` nodes for
`@brief`, `@param`, `@tparam` and `@return`, also accepting backslash commands.
The full physical comment remains authoritative: unknown commands and free prose
are retained, even when no structured entry is created. Each entry exposes its
kind, name, description, tag range and exact parameter-name range. Its `bindings()`
keep the exact callable declaration and parameter (or template parameter) target
for each include/configuration occurrence. These are non-owning references and
are remapped during copying. JSON exports the ordered entries, target/declaration
IDs and translation-unit/include context; DOT displays both ownership and binding
edges. Unbound names carry diagnostics rather than guessed links.

`analysis.Documentation.forDeclaration(project, callable)` combines documentation
from linked declarations and definitions. `commentsForDeclaration` keeps their
separate comments and briefs; repeated physical header text is returned once.
`forParameter(project, parameter)` follows the parameter position across those
linked declarations, even when a prototype says `count` and its definition says
`n`. Queries do not rewrite the original binding to the other occurrence.

Use `utilities.Documentation.renameParameter(project, parameter, "amount")` for
an explicit rename. It returns an independent project and changes only the exact
parameter identifier, resolved code uses, and names of tags bound to that parameter.
Renaming header `count` leaves definition `n` unchanged; renaming `n` leaves
`@param count` unchanged. Description text is byte-for-byte preserved. Compatible
repeated header occurrences are updated together. Conflicting include contexts,
macro-expanded identifiers, names that might capture another symbol, missing
provenance and unapplied syntax edits are conservatively rejected. The utility
replays captured sources with the original configurations and translation times,
rebuilding ranges, bindings and analysis; it never writes files or relaxes generic
comment-catalogue validation. Emit/reparse existing edits before calling it.

Minimal function-template declarations support owned `templateParameters()` with
`TemplateParameterDeclaration.name()` and `keyword()` (`class`/`typename`), dependent
written type references, C++ emission and JSON/DOT export. The same rename utility
updates a template parameter's bound type occurrences and `@tparam` name. This is
not general template instantiation, specialization or argument deduction support.

## Basic templates and dependent names (#34)

Written templates retain their generic body. `CallableDeclaration.templateParameters()`
and `ClassDeclaration.templateParameters()` own ordered type, non-type and
template-template parameters, including nested parameters and written defaults.
`TemplateArgument` owns a type or value expression. Qualified `NamedType` segments
own their qualifier and argument lists; `typenameKeyword()` and `templateKeyword()`
preserve the disambiguators. Expression template-ids likewise retain arguments.
Canonical emission separates closing `>` tokens for C++03 and preserves these
structures rather than reducing them to an opaque spelling.

A dependent member call such as `x.size()` in
`template<class T> unsigned long count(const T& x)` remains unresolved in the
written generic body. Basic type substitution creates a separate owned
`Project.templateInstances()` entry with its copied callable and argument types;
`origin()`, `cause()` and `source()` are non-owning links. The generic body is not
mutated. The initial #34 contract reported `basic-type-substitution` and
`complete = false`; the #35 instantiation and diagnostic contract below supersedes
that initial status without claiming full C++03 conformance.

The #36 catalogue replaces the initial #34 vector-only prerequisite. The full
built-in declaration data stays outside every returned Project. `standardLibrary()`
is an optional owned `StandardLibrary` collection with owned `headers()`; the
legacy `librarySources()` API is a flattened non-owning view of these same files.
Active modeled includes hold `libraryHeader()` references. Physical replacement
headers remain ordinary project source and never acquire catalogue privileges.
Header visibility follows the requesting translation unit and include position.

Only demanded declarations, their owner chain, selected specializations and required
dependencies are published. Merely visiting an overload candidate does not retain
it. Unused include headers may remain empty; removing the last include/use removes
the collection. Full catalogue token/source payloads are not copied into retained
header provenance. Generic classes live once under their header. Concrete class
instances remain in `classTemplateInstances()`, with only demanded concrete members;
`Declaration.templateOrigin()` links these members to their generic declarations.
Selected library object lifetimes are exposed by
`VariableDeclaration.constructorTarget()` and `destructorTarget()`.

`OpaqueLibraryType` records an implementation-defined type identity and its known
category without inventing ABI width, storage layout or a class definition.
The catalogue provides declarations only, with no container implementation or
allocator behavior. Resolved library calls without behavior models remain C++
calls. DFG conservatively preserves incoming definitions and adds possible writes
to receivers, exposed arguments and accessible storage instead of claiming purity.
Opaque construction and destruction also affect accessible storage at their CFG
points. Their DFG source is the owned object declaration, with lifetime target links;
this is a conservative dependency, not an invented written call.
See `library-catalogue.en.md` for per-family declaration and behavior coverage.
Native acceptance uses real toolchain headers; normal analysis needs only the JDK.

JSON exports written syntax, library declarations and semantic instances as
separate owned collections with reference IDs. JSON `standardLibrary` owns headers,
while compatibility `librarySources` contains their IDs. `LIBRARY_HEADER`,
`TEMPLATE_ORIGIN`, `CONSTRUCTOR_TARGET` and `DESTRUCTOR_TARGET` are typed semantic
edges (also exported in DOT); DOT labels catalogue and instance
nodes. Copies reconnect those references inside the new snapshot. Physical source
emission visits only `Project.files()`, preserving the original include and generic
body without emitting catalogue declarations or instance bodies. These boundaries
are checked by `TemplateExportTest` and the template corpus scenario.

Instance analyses use the language and platform profile of their requesting
translation unit. CFG/DFG, conversions, type normalization and constant folding
visit the separate concrete body without modifying the generic template.

## Specialization syntax and instantiation exports (#35)

`templateKind()` distinguishes primary templates, full specializations, class
partial specializations and explicit instantiations from ordinary declarations.
`templateArguments()` owns the written specialization arguments;
`hasTemplateArguments()` also preserves an empty `<>`. `exportedTemplate()` records
written C++03 `export`. Canonical source emits these declarations, including
`template <>` and `template struct Box<int >;`, while physical source never adds
synthetic instance bodies or library catalogue declarations.

For callable instances, `TemplateInstance.origin()` identifies the generic
declaration used for deduction, `selected()` identifies the actual definition
copied, and `declaration()` owns the substituted callable. The origin may be a
forward declaration or primary template while the selected definition is a later
definition or explicit specialization. JSON exports separate `originId` and
`selectedId` references; both remain project-owned across copies and rebuilds.
Source-position rules for an instantiated body use its selected definition.

`TemplateInstance.requests()` records all direct demands, including explicit
instantiation declarations. `causes()` filters call requests; the legacy `cause()`
returns the first call or null for an explicit-only instance. JSON includes
`requestIds` and `causeIds` (and legacy `causeId` when present). A call's
`CALL_TARGET` edge points to its callable target, whereas `INSTANTIATION_CAUSE`
points from the requesting syntax node to the instance wrapper.
`INSTANTIATION_REQUEST` is the reverse edge, allowing traversal to every request.
The two relationships are deliberately not interchangeable. Nested requests belong
to the concrete outer body, not the untouched generic template. Class instances
are separately owned by `Project.classTemplateInstances()`: `origin()` is the
primary, `selected()` the chosen declaration, and `declaration()` the concrete
owned class. Their argument and request IDs are exported in the same snapshot.
All reference IDs must name project-owned nodes; export rejects dangling edges.

Node diagnostics export `code`, `severity`, `message`, `primaryRange` and ordered
`relatedRanges` when available. An instantiation failure's primary position is the
failing expression, not its caller; the substituted type is in the message.
Request ranges follow from the immediate enclosing instantiation to its caller.
These are semantic diagnostics, separate from parser `parseProblems` and their
preprocessing occurrence groups. Inspect diagnostics even without CFG/DFG.

The written `export` marker is not proof of separate-template compilation support.
`scripts/probe-cpp03-export.sh` reproduces a header declaration, separate exported
definition and client translation unit. GCC 14.2 and Clang 19.1.7 warn that exported
templates are unsupported/ignored, then fail linking the requested specialization.
Adding an explicit-instantiation definition links and runs on both, but is only a
control, not an implementation of `export`. Cross-TU sharing of a definition
visible in a shared header is a different test. General C++03 exported-template
lookup and instantiation must not be inferred from either result.

The implemented callable deduction path handles type parameters, explicit integral
value arguments, deduction from array bounds, and template-template arguments
from structured template-ids. It checks immediate-context signature substitution
before selecting a candidate;
a missing dependent nested typedef can remove that candidate (SFINAE), while an
invalid member access in the selected concrete body remains an error. This is not
a claim of every non-type/template-template deduction context, all expression
SFINAE forms, or complete two-phase lookup. Class specialization selection matches
structured argument patterns and compares partials by deduction in both
directions; incomparable viable partials are diagnosed rather than selected by
source order. Full specializations and explicit instantiations are distinct
written declarations, not silently rewritten primaries. Function partial
specializations are rejected. Class instance construction has a separate
`template-instantiation-limit` resource diagnostic; nested callable construction
has `template-instantiation-depth`. An exhausted budget must not be presented as
a successfully complete analysis. `unsupported-export-template` reports the
semantic boundary even though the written export marker round-trips.

Callable `TemplateInstance.structuredArguments()` owns ordered `TemplateArgument`
nodes of kind TYPE, VALUE or TEMPLATE. The compatibility `arguments()` view returns
their existing type payloads, not separately owned copies, and omits value
arguments. JSON nests each node once under `structuredArguments`; it exposes
`structuredArgumentIds` for those wrappers and `argumentIds` for the compatibility
type view. There is no second nested callable-instance `arguments` array. Class
instances retain their own structured `arguments` array. DOT follows the same
single-ownership children and labels every argument kind. Copies preserve the
wrapper/payload relationships while reconnecting all references in the new
snapshot; `TemplateInstantiationExportTest` verifies unique JSON IDs for all
three callable argument kinds and confirms source does not emit extra bodies.

A callable instance now reports `status = instantiated` only when its body is
present and complete, contains no ERROR finding, dependent expression or
unresolved call; otherwise it reports `incomplete-instantiation`. `complete()`
exposes the same per-instance condition. This is a local analysis result, not a
claim that every C++03 deduction or instantiation rule is implemented.

`Project.hasErrors()` now includes semantic ERROR findings as well as parser
failures. Use `parseProblems().isEmpty()` to distinguish successfully parsed but
semantically invalid source. `semanticDiagnostics()` returns the immutable
findings independently of flow generation, including ordered instantiation
request ranges. Root-level resource-limit findings survive CFG/DFG passes.

## Common vector semantics (#37)

GREEN/FOLDED snapshots own `dynamicArrayTypes()` and `dynamicArraySemantics()`.
These are shared semantic nodes, not replacement C++ syntax. A type references its
concrete trusted library class, element type and written type sites. An operation
references its original syntax, destination object, ordered sources, common type
and selected C++ target. JSON exports those references and DOT displays typed
`COMMON_TYPE`, `COMMON_OPERATION`, `SOURCE`, `DESTINATION`, `CPP_TARGET` and
`ELEMENT_COPY_CONSTRUCTOR` edges. Physical source generation retains the original
vector spelling and calls; these nodes do not promise Java container lowering.

For ordinary `std::vector<T, std::allocator<T> >`, kinds distinguish EMPTY, COUNT,
FILL, RANGE, COPY, ASSIGN_COPY, ASSIGN_FILL, ASSIGN_RANGE and APPEND. Recognition
uses resolved library identities and overloads, not method names. Scalar elements
support these operations; ranges currently require pointers. Record append, fill
and copying select an accessible explicit copy constructor from
`const T&` when present and retain that callable as an effect link. Implicit
record copies have no invented callable; their subobject/destructor effects
remain conservative. No exact internal copy
count, allocation strategy, capacity growth or deep pointee copy is implied.

DFG edges read ordered inputs into the common operation and use it as the new
container definition. Assignment updates the existing object, including references
to it; copying creates a distinct container. Pointer elements preserve source
identity and may still share pointees. Available element-copy effects remain
conservative. Edits to syntax, selected target bindings, signatures or provenance
invalidate derived facts; reanalysis reconstructs them for the new snapshot.

`vector<bool>` proxies, custom allocators, explicit allocator arguments, custom
iterator ranges, Record count construction and Record assignment retain resolved
C++ operations when equivalent common effects are not established. Unknown
library effects remain conservative. Element access, size/capacity, iterator
validity, insertion/erasure and swap are separate #38 groups, not inferred from
this initial construction/copy/append model.

## Vector access, properties and handle lifetime (#38)

Common operations distinguish SUBSCRIPT/FRONT/BACK/AT, SIZE/IS_EMPTY/CAPACITY/
MAX_SIZE, RESERVE/RESIZE, POP_BACK/INSERT/INSERT_FILL/INSERT_RANGE/ERASE/ERASE_RANGE/
CLEAR, BEGIN/END/RBEGIN/REND and SWAP. They retain the selected C++ overload and
written operands. `resultKind()` distinguishes a value, reference or iterator;
`index()`, `writable()` and `boundsChecked()` describe access. Checked `at` keeps
its exceptional CFG path. Queries read the container but do not write it or invoke
copy effects. `mutatedObjects()` lists actual destination objects, including both
swap operands. Element writes and container aliases are distinct DFG relationships.

`handle()` records the produced reference/iterator's sequence, position kind,
optional written index, direction, writability and conservative validity. A valid
end iterator is still not dereferenceable. Creation descriptors never silently
change when later code mutates the vector. Each mutation instead exposes
`handleEffects()` with the earlier producer, validity after the mutation and,
when known, storage association after swap. JSON includes these records and typed
edges; copying a Project rebinds every reference to the new snapshot.

Straight-line known counts and reserved capacity can prove that an earlier
reference survives insertion. Exact capacity growth is never assumed. Control
flow or unmodeled calls remove unsupported proofs; unknown reallocation is marked
UNKNOWN, not safe. Swap preserves references to elements while exchanging their
sequence association; references to container objects keep their identity.
Abstract catalogue iterators carry trusted container/difference-type links rather
than pretending to be machine pointers. Their physical representation stays opaque.
Nontrivial user effects remain conservative and unresolved common equivalence stays
as the original C++ operation. This is not a complete path-sensitive STL verifier.

## Other container families (#39)

`Project.containerTypes()` and `containerSemantics()` own separate common nodes
for deque, list, set/multiset, map/multimap and stack/queue/priority_queue. They do
not inherit vector reallocation rules. Types expose value/element, key, mapped,
comparator and underlying-container links where applicable, key uniqueness,
iteration category, ordering and reference-stability family. A set/map is unique;
a multi-container may retain equivalent keys. Adapter order is LIFO, FIFO or
priority, rather than an invented general iteration order.

Operations retain raw C++ syntax and selected overloads, ordered inputs, result
category, modified objects and available element/comparator implementation links.
FIND reads without inserting; map SUBSCRIPT may insert a default mapped value.
User comparator calls may have observable effects even during lookup, so a
read-only container query does not imply a pure whole-program operation. Exact
comparison counts and tie order are not assumed. Unsupported common equivalences
retain resolved C++ and conservative effects.

List SPLICE records source/destination and ALL/ONE/RANGE transfer scope. It moves
elements, not container objects, and preserves references to transferred elements.
Handle creation records and temporal effects describe storage association; partial
or conditional transfers remain conservative when exact membership is unknown.
An element-reference write follows transferred storage, while a reference to the
source container continues to denote that original object. Deque operations keep
their own reference/iterator guarantees, and node-based erasure does not promise
that erased-element handles survive.

Trusted catalogue iterator facts distinguish random-access deque from
bidirectional list and associative containers. Set elements are read-only; map
value_type retains const key and writable mapped value. Iterators refer to owned
value-type declarations instead of assuming a pointer ABI. JSON/DOT include
CONTAINER_TYPE, SOURCE_CONTAINER, COMPARATOR_TARGET and type/handle edges; copy
and edit invalidation preserve the same ownership rules as vector annotations.

## Character sequences and algorithms (#40)

`Project.characterSequenceTypes()` and `sequenceSemantics()` retain owned common
facts for trusted C++03 string, iterator and algorithm operations. A character
sequence keeps its character, traits and allocator types. Explicit-count and
iterator-range operations preserve embedded NUL characters; null-terminated
input is a separate length contract. Append mutates its destination, find reads
its input, and substr produces an independent sequence rather than an alias.
Append/assign return a whole-sequence reference, distinct from a character
reference; chained access and saved aliases retain the original string storage.
Unproven substring-copy constructor/append/assign overloads with a source-string
position remain raw C++ rather than being mislabeled as counted buffers.

Sequence operations keep selected C++ targets and written operands. Range-first,
range-last, second-range, input-sequence and output-destination edges distinguish
reading, in-place modification and output-iterator writes. Back insertion names
the receiving container. Traversal direction is recorded only when known.
REMOVE returns a logical end and changes values without shrinking physical size;
a later erase is a separate container operation. ACCUMULATE retains the initial
value and accumulator/result type, rather than inferring them from element type.

A callable implementation is linked when resolution proves its target. Its
possible effects are separate from the algorithm's range writes: a predicate or
comparator may modify globals even when the algorithm only reads its range.
Unknown implementations remain conservative; callback invocation counts and
unspecified ordering are not invented. Original C++ syntax and unsupported
operations are preserved. JSON and DOT export the same owned type, range,
callable, result and mutation relationships, including copied snapshots.

## Contextual object data flow (C++03)

`FunctionFlow.DataEdge` retains `from`, `to` and `kind`, and adds nullable
`callSite()`. An intraprocedural edge has no call site; the three-argument
constructor remains available. For an edge carried through a function call,
the site is the invocation in the owning caller flow. A callee assignment can
therefore reach reads in two callers without conflating their actual objects.
Nested effects are attributed to the outer invocation in that caller's flow.
JSON exports `callSiteId` on contextual edges, and DOT labels the invocation.
Diagnostic-only snapshot copies reconnect the endpoints and invocation to the
new snapshot. Ordinary graph edits discard stale flow results for reanalysis.

C++ object memory distinguishes pointer bindings from pointees, references from
stored objects, and known fields/elements from their containing object. An
external call through a writable reference or pointer may add a write; passing
an address does not prove the previous value was overwritten. For example,
`int x=1; update(x); use(x);` with declaration-only `void update(int&)` retains
both the initialization and the specific call as possible reaching sources.
For an ordinary `CallExpression`, an available body can prove an exact-object write
on normal return. Conditional
writes and uncertain aliases retain the earlier source. Normal and exceptional
outcomes are separate, possible virtual targets are joined, and recursion uses
stable conservative effects rather than an assumed finite execution depth.
`UnreadWrites` considers reads of contextual body sources across function flows.

The API/JSON tests cover external versus body-proven writes, two invocations,
aliased reference arguments, returned pointers/references, fields/elements,
recursion and exception paths. The native `interprocedural-dfg` scenario checks
source round trips separately. Unknown memory and unavailable targets retain
possible effects; this is not a whole-program proof of all C++ alias behavior.

Implicit constructor/destructor invocations and overloaded operator syntax retain
conservative local syntax/object effects even when their bodies are available.
Their prior definitions remain possible; they do not claim body-proven kills or
foreign-body invocation IDs. Exact contextual body attribution currently applies
to ordinary `CallExpression` nodes, including resolved member and virtual calls.
Reference-member bindings that are not proved, indirect data-member selections
and pointer-arithmetic joins use conservative possible locations. They can add
sources without proving an overwrite. Pointer-location widening keeps loop and
recursive analysis finite; it does not assume a target object layout or ABI.
Repeated or unresolved base-subobject paths use weak field views; distinct resolved field declarations remain separate even when their names match.

### Safe editing, renaming and generation (#43)

An ordinary structural `ProjectEditor.build()` produces a new snapshot and
rechecks diagnostics. Repairing an `ErrorExpression` removes that occurrence's
parse problem; independent findings and other remaining errors are retained.
The input snapshot remains unchanged. A conflicting declaration remains in the
graph with its peer, a semantic diagnostic and `Project.hasErrors() == true`.
Graph construction and source emission are separate operations: an invalid
snapshot can be inspected/exported, but cannot generate C++ sources.

`DeclarationRename.affectedDeclarations(project, declaration)` previews the
selected declaration/redeclaration family and, for virtual methods, all connected
overrides. `DeclarationRename.rename(project, declaration, newName)` returns a
new snapshot with resolved uses and using declarations renamed. It does not write
files. An uneditable external family member prevents the operation before any
partial result is published. Independent declarations with the same spelling are
not part of the family. Inspect the returned root status before generation.

C++ emission checks lookup in the resulting scopes against selected source-graph
targets. A still-valid spelling is preserved; a namespace/global qualification
may restore the selected target after an added using directive. If no safe
spelling can be established, or an ambiguous occurrence has no selected target,
generation rejects the snapshot before files are written. Multiple valid virtual
dispatch implementations are not themselves a lexical ambiguity.

The existing C++03 corpus remains the round-trip coverage for accepted syntax,
precedence, declarations, headers and translation-unit boundaries. This does not
make every C++ construct portable to Java: `Source.languages` and generation's
portability checks continue to reject unsupported cross-language constructs.

Namespace, namespace-alias and class renames follow resolved qualifier components,
including reopened namespaces and out-of-line constructor/destructor spellings.
Unproved or ambiguous components, macro-derived identifiers, conflicting include
contexts and unavailable physical sources are rejected rather than partly renamed.
Existing structural edits must be emitted and reparsed before physical-token
renaming. Partial overload-set using declarations and accidental name capture
produce an erroneous result rather than silently changing imported targets.

Binding-preservation validation requires a whole `Project`: it applies to
`Source.files`, `Source.physicalSources`, `Source.render(Project, ...)` and
`Cpg.sources`. Rendering an isolated node or `SourceFile` is a context-free
fragment printer, not proof that the fragment preserves project-wide lookup.

### Snapshot transformation history and standalone JSON (#44)

`Node.transformationProvenance()` exposes immutable links `(passId, role, source)`
to immediate predecessor nodes. These links are neither owning children nor
current-snapshot semantic references. Copying a node retains historical identity;
transforming a construct records the responsible pass and its source root and
contributors. Unchanged unrelated syntax does not acquire artificial copy history.
For console normalization, stream, value, newline and flush are distinct roles;
C++ `std::endl` contributes both newline and flush. Folding `2 + 3` into `5` links
the new literal to the preceding expression, whose own history reaches the source
expression and its coordinates. Earlier snapshots remain immutable and usable.

`Project.transformationResults(originalNode)` queries the selected snapshot for
all results reachable from an original node, with pass and contribution role.
It computes the reverse view from existing forward links rather than storing a
second set of edges. `Project.snapshotStage()` explicitly identifies parsed,
resolved, green or folded, even when a stage contains no common nodes or retains
errors that prevent transformation.

JSON is a one-way export, not a `Project` persistence/import format. The root
contains numeric `schemaVersion: 1` and the selected `stage`. During development
the schema version remains 1; after stabilization optional compatible fields keep
the version while incompatible changes require a new one. IDs belong only to one
JSON file. The same inputs and parameters produce deterministic numbering; source
edits may renumber everything.

The current snapshot remains nested. `history` contains reachable historical node
definitions once, with child references `{nodeId: ...}`. Transformation links carry
`passId`, `role` and `sourceId`. Historical Project/SourceFile context is shallow,
so it does not import unrelated old syntax. Necessary semantic dependencies and
source context are included to make IDs and coordinates self-contained. A plain
JSON parser can follow `5` to its folding pass and earlier `2 + 3` without opening
source files. No JSON-to-Project importer is provided.

Historical semantic declaration dependencies are identity/context summaries, not
copies of every old member or function body. Actual provenance inputs and their
owned operand syntax remain complete. If a declaration is itself a provenance
input, it is promoted to the full historical representation. This distinction
keeps library dependencies from importing unrelated historical catalogue syntax.
`historyDependency: true` marks such a reference-only descriptor. It omits owning
children, semantic outgoing edges and token-unit expansion; its identity, range
and available type information remain readable.

### Pass execution history and result freshness (#45)

`Project.passRuns()` records completed public pass executions in order, including
runs with no changes or diagnostics. This is distinct from node transformation
provenance: an unchanged result can still prove that an analysis was executed.
Each immutable `PassRun` has a sequence, pass identifier, project/function scope,
aggregate status and per-function results. Project-wide runs freeze their covered
functions; a later added function does not inherit a successful earlier check.
`Project.passStatus(passId, callable)` selects the latest applicable result.
An absent status means no applicable run, while `STALE` means recomputation is
required. Older snapshots retain their own history and results after edits.

`UnreadWrites.analyze(project, callable)` checks one function and preserves the
other functions' unread-write results. The one-argument overload checks the whole
project. Missing or stale dependencies are recomputed before inspection. CFG and
DFG dependencies may cover the entire project because memory flow crosses call
boundaries; the history records their actual scope and execution order rather
than pretending the prerequisite work was limited to the selected function.

JSON adds ordered `passRuns`, separately from historical syntax in `history`.
Run entries contain scope, status and covered results; live function references
use file-local IDs. Removed functions retain descriptive stale results rather
than dangling references or imported old function bodies. There are no timestamps
or random identifiers, so repeated equivalent inputs remain deterministic.

### User-defined passes and typed products (#46)

The public `com.kniazkov.cpg.passes` API provides `AnalysisPass` and
`TransformationPass`. Both declare a `PassId` and `requires()` dependencies.
Register them with `PassRunner`, then call `run(project, id)` or the overload
with a callable. `BuiltinPasses` exposes IDs for the existing analyses.
Dependencies are validated before execution and prepared deterministically.
A requested pass always runs; current prerequisites can be reused. If a later
transformation invalidates an earlier prerequisite, the runner prepares it again.
Cycles, missing registrations and dependencies that cannot stabilize are errors.

An analysis receives immutable `PassContext` input and an `AnalysisResults` sink.
Use `put(node, key, value)` and `diagnose(node, diagnostic)` to stage a complete
producer result set. `ResultKey<T>` includes producer ID, name and Java type,
so two producers can both publish `range` on the same node. `resultOf(node, key)`
on the project/context returns only current values; missing, stale, deleted or
foreign targets return empty. `resultStatus`, `analysisResults()` and
`analysisDiagnostics()` expose retained stale metadata separately.

`ResultTypes.defaults()` handles strings, booleans and standard numeric types.
`with(IntRange.class, r -> Map.of("min", r.min(), "max", r.max()))` registers an
outgoing conversion; construct the runner with that registry. Custom Java values
must themselves be immutable, for example records with immutable components.
The encoder output is validated and deeply frozen during publication, so a later
mutation of its map/list cannot change an old JSON snapshot. Encoders do not run
again during export; no decoder or JSON-to-Project importer is required.

Successful analysis publication atomically replaces this producer's values and
diagnostics within the requested scope, including removing omitted keys. Other
producers and the producer's other scopes retain their products subject to
freshness. Diagnostic ownership is independent of diagnostic code or message.
A callback, validation or encoder exception publishes no partial result set and
leaves the caller's snapshot unchanged. Arbitrary custom analyses may read the
whole project, so source/binding edits conservatively stale custom products;
rerunning a prerequisite also stales its declared dependent producers.

A transformation stages edits in the supplied `ProjectEditor`. The runner applies
one batch, fixes the registered provenance ID and records completion. Calling
`build()` within the callback or using an escaped editor afterward is rejected.
Function-scoped transforms may reference declarations elsewhere in the project,
but cannot write outside their scope, remove the selected callable or replace it
with an unrelated identity. A same-lineage callable edit is permitted.

JSON adds producer-owned `analysisResults` and `analysisDiagnostics` when present,
with file-local node references and current/stale status. Custom history entries
include the custom marker and sorted nonempty requirements. Removed node products
are omitted from the new snapshot; the older snapshot retains them.
The standalone example in `examples/cpp03-custom-passes` is compiled against the
packaged JAR alone and demonstrates the complete API without package-private access.

Function scope includes the owned subtree, including methods of local classes.
Its history covers those nested callables. Combining separate function runs does
not substitute for a whole-project custom invocation: global-node products need
the callback to run with project scope.

### Lattice values and opt-in integer ranges (#47)

`com.kniazkov.cpg.lattice.Lattice<E>` defines `lessOrEqual`, `join`, `meet`,
`bottom()` and `top()`. Its immutable values implement `LatticeElement.toJson()`.
The result encoder calls this interface, including for an external custom domain;
it does not select an encoder by a list of known domain classes. An explicitly
registered exact-type encoder still takes precedence. JSON is frozen once at
publication, just like other custom results.

`IntegerDomain.INSTANCE` is a language-independent mathematical integer lattice.
Its factory produces immutable `IntegerValue` objects with inclusive `BigInteger`
bounds. `constant(7)` and `interval(7, 7)` are equal normalized constants;
`interval(7, 9)` remains an interval. Reversed bounds produce bottom, and unbounded
ends can be created with `atLeast` or `atMost`. Join computes the interval hull:
joining 1 and 10 yields `[1, 10]`, not a two-element set. Consequently 5 remains
possible in this approximation. Bottom means no possible integer; top means any
integer. Neither is the same as an absent published `ResultKey` value.

`IntegerRanges` is an explicitly registered `AnalysisPass`, with public `ID` and
`RANGE` key. Register `new IntegerRanges()` with `PassRunner` and request its ID,
or declare that ID in another pass's `requires()`. Ordinary parsing, building,
export and result queries do not execute the interval pass. A dependent request
prepares missing ranges first and records the actual invocation order. Stale
results follow the same conservative freshness contract as other custom passes.

The first range pass deliberately supports a small, conservative scalar subset:
automatic nonvolatile `int`/`bool` locals, by-value parameters, pure literals and
names, unary `+`, `-`, `!`, arithmetic `+`, `-`, `*`, comparisons, safe conversions,
standalone assignment/increment, and structured loops/branches/returns. Integer
bounds come from the target platform profile (Java uses 32-bit `int`). Possible
machine overflow loses precision to top; the mathematical lattice does not
silently justify overflowing source arithmetic. There is no branch-condition
refinement. Repeatedly growing loop states widen to top after a bounded number
of joins.

Calls, aliasing, members, static/global state, volatile accesses, unsigned or
other unsupported numeric types and unsupported expression/control-flow forms
make the callable conservative: all its published ranges become top. This is a
soundness boundary, not a claim to analyze all C++03 arithmetic precisely. For a
supported callable, unevaluated nodes retain bottom. Expression products describe
possible evaluation values; declaration products describe initialization rather
than the variable's final value, and assignment-target products describe written
values. Parameters start unknown (`bool` is bounded to 0 and 1).

## Switch converted-case diagnostics (#88)

`duplicate-case` compares certified integral constant expressions after conversion
to the promoted condition type using the translation unit's `PlatformProfile`.
This is diagnostic typing, not a claim of complete switch admissibility or modeled
runtime conversion-function call edges and effects.

- `enumUnderlyingType`: `int-first` (default), `unsigned-first`, or `unknown`.
  The first two try `int`, `unsigned int`, `long`, `unsigned long` and
  `unsigned int`, `int`, `unsigned long`, `long`, respectively, subject to the
  representability and size restriction of printed C++03 7.2/5. Promotion then
  uses the full underlying-type range under 4.5/2, not just enumerator values.
- `signedIntegerConversion`: `unknown` (default) or opt-in `twos-complement`.
  The latter specifies out-of-range signed integral conversion under 4.7/3;
  it does not make signed arithmetic overflow defined.
- `bitFieldSignedness`: `signed` (default), `unsigned`, or `unknown`, independently
  of ordinary `char` signedness. This choice applies to plain `char`, `short`,
  `int`, and `long` bit-fields, not explicitly signed or unsigned declarations.
  Direct integral fields promote by their value width; excess width is padding.
  Enum bit-fields use enum promotion regardless of width. Arithmetic uses shared
  expression promotions; explicit casts and comparisons keep their result types.

Class conditions require exactly one visible, nonhidden integral/enum conversion
function, with inherited lookup, reference results, cv-callability and access
proved. Multiple such functions remain unknown even if they return the same type:
the later unique-target relaxation is not substituted for printed 6.4.2/2.

Before CWG324, assignment/compound-assignment, prefix increment/decrement,
comma-with-field-right-operand and conditional-field result provenance remains
unknown. Postfix increment/decrement instead uses the full declared result type
under 5.2.6, not the operand's bit-field width. Merely containing a field does not
make an otherwise unambiguous expression unknown.

Enumerator and certified const-case evaluation uses a snapshot-owned
`IntegralConstants` context shared with `TypeSemantics` and `StandardConversions`.
It certifies enum ownership and static/nonstatic membership, memoizes successful
values and guards cycles. Enum/const names in widths and enum initializers remain
available inside nested unary-plus and arithmetic promotion expressions.
Reference condition declarations and protected-conversion access use the actual
receiver type, not only the declaring class.
Integral-constant-expression syntax under 5.19 is checked across all arms,
including unevaluated conditional arms; volatile values, references, nonstatic
members and object-member calls are not accepted as constant proof. Unknown
profiles or conversions do not invent converted keys. Proven equal raw constants
can still establish a duplicate independently of an unknown target conversion.

Integer-to-enum ICE casts are supported conservatively only between the minimum
and maximum enumerator values (zero for an empty enum). This subset does not
implement the entire enum value range of printed 7.2/6. The discrepancy between
that paragraph's normative wording and footnote 81 is not silently resolved by
adopting later CWG58. Direct floating-zero-to-integer casts are supported;
nonzero floating literals remain unknown, not invalid. The profile gives storage
sizes but no target floating representation or rounding policy. Neither this
boundary nor an imported C defect-report interpretation proves broader C++
constant-expression conformance.

Regression assertions are in
[`SwitchConversionCompletenessTest`](../src/test/java/com/kniazkov/cpg/SwitchConversionCompletenessTest.java),
with existing `SwitchCaseConversionTest` and profile checks in `PlatformProfileTest`.
[`SwitchCaseConversionsIT`](../src/test/java/com/kniazkov/cpg/e2e/SwitchCaseConversionsIT.java)
adds CLI/API/JSON agreement, explicit-profile cases and strict-compiler checks.
See [validation and historical observations](cpp03-paragraphs/probes/DiagnosticRegressionResults.md)
for execution evidence and limits. Unsupported/non-evaluable cases remain unknown;
these assertions do not establish complete coverage of the cited paragraphs.


## Original-2003 nested access (#89)

The nested-access profile follows the supplied printed ISO/IEC 14882:2003
§11.8/1, not a silently adopted later correction. Nested/enclosing membership
alone grants no mutual private access; friendship of an enclosing class is not
inherited by its nested class. Access is checked on the selected declaration,
without replacing an inaccessible selection with an accessible namesake.
The immediate class context, direct friendship and ordinary self/public access
are distinguished.

Field/variable uses report `inaccessible-member`, written named types and typedef
uses report `inaccessible-type`, and selected callable uses retain
`inaccessible-overload`. Lookup bindings remain available for inspection.
Checks concern the written alias, not blanket rejection of its underlying type.
The informative §11.8/2 example distinguishes the nested declaration's base-clause
from its members: an enclosing private base name is permitted in that base-clause;
unqualified injected names of the class/base remain usable, whereas an explicitly
qualified enclosing private name does not acquire that exemption.

[Regression evidence and remaining gates](cpp03-paragraphs/probes/NestedAccessRegressionResults.md)
separate source/test inspection from executed validation. This is a bounded access
fix, not complete class-access, inheritance, template or C++03 conformance.

Review regressions also preserve a same-named field's precedence over an injected
class name in expressions, while type contexts retain class binding. Friend-class
lookup accepts using-declarations and inherited class declarations, but a
using-directive alone does not confer friendship. Copies/re-resolution retain
snapshot-owned selected bindings. These are tested boundaries, not a complete
friendship or lookup implementation.

### Library macro settings (#92)

The size table above does not determine floating representation. `floatingFormats`
is a separate map keyed by `float`, `double`, and `long double`, accepting
`unknown`, `binary32`, `binary64`, `binary80`, or `binary128`. The default model
explicitly chooses binary32/binary64/binary128, while `floatingRounds` defaults
to 1 (nearest). It accepts the C90 codes -1 through 3; -1 means indeterminable.
`nullPointerConstant` chooses `0` (default) or `0L`; `maxMultibyteLength` explicitly
supplies `MB_LEN_MAX` (default target value 16), rather than deriving it from
integer widths or the JVM encoding. These choices are model facts, not a claim
that a native compiler on the analysis machine uses this ABI.

With `inherits: "none"`, omitted new format choices remain `unknown`, rounding
is -1, NULL is `0`, and the explicit minimal multibyte bound is 1. Existing complete
profiles remain readable, but using built-in `<cfloat>` requires all three known
formats. Formats must fit their storage, preserve nondecreasing precision, and
meet the C90 minimum precision for each type. Every setting participates in
profile identity and `toSettings()`/`toBuilder()` round-trips. See the
[macro inventory and verification](cpp03-library-macros-verification.md) for the
implemented subset; these facts do not implement arbitrary floating arithmetic.

### Assertion runtime selection (#92)

`cRuntime` accepts `"unknown"` (default, including older complete profiles) or
`"glibc"`. It participates in effective settings, profile identity and build
variants. The glibc assertion binding requires 8-bit bytes/char, 32-bit int and 32- or 64-bit pointers; those
checks do not describe a complete architecture ABI. The caller must choose a
compatible target C calling convention, pointer ABI and actual glibc library
exporting __assert_fail. LP64 or Linux alone does not select that runtime.

Unknown enabled assertions retain definition/provenance metadata including
`unavailableReason`; invocation emits `unavailable-library-macro` error JSON and
blocks generation. NDEBUG-disabled assertions need no runtime. Selected glibc
uses ordinary resolved C++ calls with conservative effects; this packet does not
infer a terminal CFG edge merely from the helper's spelling. For source-location
preservation and graph-edit boundaries see [the assertion report](cpp03-assert-verification.md).

### Typed runtime integer facts (#92)

`runtimeIntegerMacros` is an optional profile object, empty by default (including
older complete profiles). It carries selected target facts, not arbitrary C++
replacement text. Each recognized name maps to exactly a type and decimal string:

```json
{"inherits":"default", "runtimeIntegerMacros":{
  "EDOM":{"type":"int","value":"33"},
  "ERANGE":{"type":"int","value":"34"}
}}
```

These example values are explicit choices, not portable defaults. The supported
set has 30 names from cerrno, clocale, csignal, cstdio and cstdlib; the inventory
lists every name and source locator. This stage accepts only int expressions.
That is a supported-profile restriction, not a claim that every original C90
integral macro has type int. Decimal strings avoid JSON numeric truncation;
canonical value spelling and name order participate in profile identity, variant
identity, copy and effective-settings serialization. Unknown names/types,
malformed values and values outside the selected target int range are rejected.
Negative values, including the target int minimum, preserve their expression type.
Profile files and embedded compilation-database profiles require strict JSON with
unique decoded keys; duplicate names/fields cannot silently overwrite facts.

Partial fact sets are intentional. Including a built-in header installs each
modeled macro, but omitted facts have an `unavailableReason` and no invented
replacement. Ordinary or #if expansion reports `unavailable-library-macro`.
`defined(NAME)` and #ifdef report definition presence, not knowledge of its value.
An unused include remains usable. Actual supplied headers retain precedence, and
macros do not leak across translation units. Neither cRuntime=glibc nor the JVM
or build host fills in missing values.

The validator checks family-specific nonzero/distinct/range constraints. Original
C90 error selectors need not be positive, and EXIT_SUCCESS need not equal zero.
Checking a number does not prove runtime exit, locale, signal, random-number or
I/O semantics. At this integer-fact stage, runtime bindings and other macros
remained separate work. Later sections cover errno, MB_CUR_MAX and standard
streams; typed clock/wide/math macros, handler pointers, offsetof, varargs and
setjmp remain open.
See [verification and exact boundaries](cpp03-runtime-integer-macros-verification.md).

Trusted declaration-only strstream parsing may retain an unavailable EOF token
in a default argument with its metadata; it does not invent a value. This internal
mode is explicit, never inferred from a filename. User code and #if still diagnose
missing facts.

### Mutable runtime errno (#92)

The existing cRuntime=glibc selection also enables errno under cerrno/errno.h.
Its CPG-owned object-like macro expands to `(*::__errno_location())`, with the
real global C-linkage `int* __errno_location() throw()` declaration. It is an int
lvalue: assignment, compound assignment, increment, reference binding and
address-taking use live runtime storage. sizeof remains unevaluated. No fake
extern int errno or implementation body is emitted. Selecting a runtime family
still requires compatible target int/pointer/calling ABI and an actual exported
accessor; it does not infer EDOM, ERANGE or EILSEQ values.

The unknown runtime mode records an unavailable definition, permits an unused
include, and reports unavailable-library-macro on use. Header aliases and
translation units keep their normal preprocessing and ownership boundaries.
The accessor's compiler const attribute is not treated as an immutable pointee
or a promise that errno survives later library calls unchanged. Successful
library calls may change errno too; this packet adds no such value inference.
See [runtime checks and conservative memory boundary](cpp03-errno-verification.md).

### Locale-sensitive MB_CUR_MAX (#92)

An explicit `mbCurMaxBinding: "glibc-x86_64-lp64-mb16"` selects the current
locale query. The default is `unavailable`, including when cRuntime=glibc.
Selection validates cRuntime=glibc, bytes8/int32/long64/pointer64, exact
sizeType=unsigned long and maxMultibyteLength=16. Mismatched selected contracts
are rejected, not silently repaired. Existing errno/assert profiles remain valid.
Default-based profiles already have bound16; an inherits=none profile omitting
the optional bound retains legacy fallback1 and cannot select this contract.

The capability participates in settings, copying, profile and variant identity.
MB_LEN_MAX is the selected whole-runtime bound; MB_CUR_MAX calls
`::__ctype_get_mb_cur_max()` to read the current locale. Its genuine global
C-linkage return type equals canonical selected size_t. The scalar rvalue stays
unknown before and after setlocale; it is never clamped, cached or substituted
with MB_LEN_MAX. sizeof remains unevaluated. Actual headers retain precedence.

CPG exposes the correct rvalue category but does not currently diagnose all
invalid assignments, increments or address-taking of scalar-returning calls.
Strict native rejection tests do not constitute CPG diagnostics. This existing
generic limitation is not hidden by a macro-name exception. The narrow target
contract still requires a matching deployed ABI and runtime export.
[Verification and remaining work](cpp03-mb-cur-max-verification.md).

### Explicit standard-stream binding (#92)

`standardStreamsBinding` defaults to `unavailable`. The supported opt-in
`glibc-x86_64-lp64` requires cRuntime=glibc, 8-bit bytes, 32-bit int, 64-bit
long/pointers and sizeType=unsigned long. It is independent of MB_LEN_MAX.
The setting participates in serialization, copying and full profile identity.
Missing capability allows unused headers/presence checks and diagnoses use;
actual headers keep precedence. Numeric checks do not discover the deployment ABI.

Self-referential stdin/stdout/stderr macros refer to genuine global extern-C
FILE* objects. Canonical library::FILE remains opaque, with no layout or
initializer. LibraryExternalObjects groups only trusted global declaration-only
C-linkage scalar objects using full stable profile, external name, canonical
type and linkage. Lookup, previous-declaration chains, demand peers and memory
roots/accessibility agree while preserving each header occurrence. Local, user,
static, namespaced, incompatible and profile-distinct objects are not merged
merely because their names match.

Selected GNU pointer cells are mutable lvalues; C90 only promises FILE-pointer
expressions. Pointer values and runtime I/O state remain unknown. No std stream
alias is supplied: std::stdout has no target, but general unresolved
ScopeMemberExpression diagnosis is incomplete. Strict native rejection does not
establish a CPG diagnostic. [Verification and limits](cpp03-standard-streams-verification.md).

## Configurable wint_t and WEOF (#92, seventh stage)

The coupled `wideInteger` object supplies `wintType`, decimal-string `weof` and
`characterDomain: "nonnegative-wchar"`. Missing or empty settings retain opaque
wint_t and unavailable WEOF; actual headers remain authoritative. The supported
types are int, unsigned int, long and unsigned long. Both wide header families,
wide signatures and char_traits use the configured canonical type. No host or
libc-family fact is inferred.

The selected type must cover the entire nonnegative wchar_t range; WEOF must
fit that type and lie outside that envelope. This deliberately stronger policy
is not ISO's character-repertoire requirement. Signed -1 is permitted, including
when wchar_t can represent it; WEOF need not equal EOF. Type-preserving constant
expressions support #if. Synthetic profiles do not authorize mismatched native
wide-library calls or claim a complete repertoire/locale model.

At stage seven the bounded inventory had 92 obligations/85 names, with 10 obligations/10
names open. Focused implementation checks support the two WEOF entries; all four WEOF native cases passed. Expanded
regression, full-suite and exact-head CI results are separately tracked
in the [verification report](cpp03-weof-verification.md). Prior stage counts and
reports remain historical. #92 and #48 remain open.

## Clock-scale target facts (#92, eighth stage)

Optional `clockScale` couples independent `clockType`, `expressionType` and
positive decimal-string `value` facts. Each type supports int, unsigned int,
long or unsigned long; value must fit expressionType, not necessarily clockType.
Missing/empty settings retain opaque clock_t and unavailable CLOCKS_PER_SEC.
Actual headers remain authoritative; no libc/host/JVM facts are inferred.

Original C90 describes arithmetic clock_t and a scale giving the number per
second of clock() values. It does not impose later C99 exact-clock_t or constant
wording. The chosen typed-literal subset supports #if without making that a
universal historical guarantee. clock() remains an opaque runtime call.
At stage eight the inventory had 93 bounded obligations/86 names and nine open obligations/
nine names. Expanded focused acceptance passed 269 units and 28 integration tests; full verification
and exact-head CI remain separately tracked in the [report](cpp03-clock-scale-verification.md).
Prior stage counts remain historical. #92 and #48 remain open.

## Explicit HUGE_VAL compiler capability (#92, ninth stage)

`hugeVal: {kind: "positive_infinity", emission: "gcc-clang-builtin-huge-val"}`
selects a closed double-positive-infinity expression, with explicit binary64
double format and 64-bit storage. Missing/empty settings remain unavailable;
libc, host or width alone never enable it. Actual headers retain authority.
The selected exact zero-argument builtin also reparses directly under this
capability. Ordinary cmath declarations remain unchanged.

CompilerIntrinsicExpression carries symbolic double/rvalue semantics and JSON,
not a fabricated libc call, numeric JSON infinity or finite BigDecimal surrogate.
No general floating evaluator, finite HUGE_VAL policy or complete IEEE arithmetic
is supplied. Original C90 permits infinity without requiring constant form.
Assignment/increment and bare-name/declaration/qualified misuse are rejected;
address of the evaluated rvalue still has the existing generic CPG diagnostic
gap despite strict native rejection. Integer #if arithmetic is not promised.

Expanded verification passed 432 units and 31 IT, plus eight separate inventory
units (440 distinct units). At stage nine the inventory had 94 bounded
obligations/87 names and eight open obligations/eight names. Full/CI
results remain separate gates in the [report](cpp03-huge-val-verification.md).
Earlier counts remain historical; #92 and #48 remain open.

## Explicit signal dispositions (#92, tenth stage)

`signalDispositionBinding: "glibc-x86_64-lp64"` selects SIG_DFL/SIG_IGN/SIG_ERR
as explicit void(*)(int) casts of 0/1/-1. The unavailable default is independent
of cRuntime. Selection requires glibc and 8-bit bytes / 32-bit int / 64-bit long and pointers, without
sizeType or MB_LEN_MAX coupling. Actual headers retain authority. These pointer
sentinels are separate from positive-int signal-number facts; no fake helper,
integer pointer evaluation or #if guarantee is introduced.

Expanded verification passed 313 units and 22 integration tests, including actual
CPG materialization and safe subprocess save/change/restore with no delivery.
Host typedef-cast SIG_ERR incidentally passes the tested #if; all selected
explicit function-pointer casts reject it. No preprocessing equivalence is claimed.
FunctionType language-linkage identity and catalogue signal C++ linkage remain
open precision gaps; no complete ISO callback or asynchronous runtime model is
claimed. The inventory has 97 bounded obligations/90 names, five open obligations/
five names. [Evidence and pending merge gates](cpp03-signal-dispositions-verification.md).
Prior counts remain historical; #92 and #48 remain open.

## POD/member/layout preparation for offsetof (#92)

Package-private PodLayout now queries conservative POD eligibility, member paths
and union-aware recursive layout under existing explicit target policies. It
returns a justified result or typed failure, not a guessed host offset. The
query is separate from generic aggregate classification and does not broaden
unrelated sizeof behavior. Enum/member-pointer/template layouts and bounds
requiring sizeof(union) remain unsupported; valid C++03 cases outside the subset
are not language errors.

Results exceeding explicit size_t representability return SIZE_NOT_REPRESENTABLE.
This necessary bound does not certify compiler maximum object sizes.
Focused checks passed 226 units and five IT, including an independent native
layout oracle and meaningful field edits. Native host offsetof is not CPG macro
support. No new macro, builtin, parser or node is enabled; offsetof remains open
and unavailable. Inventory totals stay 97 bounded obligations/90 names and five
open obligations/five names. [Evidence and remaining gates](cpp03-offset-layout-verification.md).
#92 and #48 remain open; previous packet reports retain their historical scope.

## Selected offsetof builtin subset (#92, still open)

The explicit `offsetof` object selects emission gcc-clang-builtin, layout
x86_64-sysv-lp64-v1 and a positive decimal supportedObjectBytes ceiling no larger
than 2147483647. Missing/empty settings remain unavailable. Exact scalar sizes,
alignments, size_t and natural-layout policies are checked independently of libc
and floating formats. The ceiling applies to relevant dependencies and describes
a software-supported subset, not a universal compiler maximum.

OffsetofExpression owns type/member/index operands. Snapshot-owned ICE queries
compute validated values while folding preserves the whole expression and its
transitive layout bounds/const initializers for edits. Selected capability use
refuses active pragmas and expanded pragma operators except exact pragma once;
inactive directives remain allowed. Non-POD sizeof dependencies are unsupported
subset cases rather than invalid owners. No replay guard is weakened.

Focused verification passed 325 units and 15 IT; the final eight-unit/two-IT
rerun overlaps those counts. Native checks use actual CPG materialization, not
host macro presence alone. Valid POD enum/member-pointer/template and
sizeof-union cases remain excluded, so offsetof remains open/partial. Counts
stay 97 bounded obligations/90 names and five open obligations/five names.
The preceding preparation section is historical. [Evidence and remaining gates](cpp03-offsetof-builtin-verification.md).
#92/#48 remain open; full Maven and exact-head CI are separate gates.


## Bounded offsetof contexts (#92, partial)

The current packet extends the selected query to already materialized, complete
concrete POD template instances and query-local sizeof dependencies, including
unions. Primary, explicit and partial specialization evidence concerns the
actual selected concrete instances, not general template support. The existing
explicit layout capability and ceiling are unchanged; unrelated generic sizeof
and ABI claims are not broadened.

The implementation preserves written type/non-type arguments, defaults and relevant
specialization-header dependencies through folding so edits can reselect an
instance and recheck its budget. Snapshot ownership, cycle and ceiling checks,
pragma/operator restrictions and source replay remain in force. Named non-type
arguments and dependent defaults that the generic instantiator cannot
materialize remain prerequisite limitations. Non-POD sizeof dependencies remain
valid unsupported cases, not blanket language errors.

The corrected snapshot passed all 1411 unit tests and 13 selected IT in 8:50.
Independent review passed 78 affected units and two native IT; these overlap
the clean run. Full Maven and exact-head CI remain separate pending gates. Identity-preserving syntax
copy fixes template-argument typedef ownership; derived template instances
retain caller argument aliases, excluding primary-template/catalogue
placeholders and aliases with canonical OpaqueLibraryType targets. Dependent
or unresolved typedef target chains remain unsupported.
Array typedef arguments are no longer excluded.
Qualified member aliases with dependent targets and function-pointer template
arguments retain existing generic limitations; resolved namespace aliases pass. Independent checks are not added to expanded-suite counts.
offsetof stays open/partial, with 97 bounded obligations / 90 names and five
open obligations / five names. The preceding builtin section is historical.
[Current evidence and gates](cpp03-offsetof-contexts-verification.md).
#92 and #48 remain open.


## Selected member-pointer storage for offsetof (#92, partial)

The optional memberPointerStorage="itanium-x86_64-lp64-v1" setting independently
selects data-member pointer storage 8/8 bytes and member-function pointer storage
16/8 bytes (size/alignment). A pointer to a function-pointer data member uses
8/8. Omission remains unsupported; ordinary-pointer width, glibc and host
properties do not enable it. No member-pointer value, encoding or call is modeled.

Known incomplete, non-POD or oversized owner bodies and parameter/return class
bodies need not be laid out or fit the query budget. Written type/signature and
template syntax must still resolve; its sizeof/constant dependencies remain
bounded and survive folding. An edit crossing the ceiling is rejected. Owner
identity alone is not unconditional acceptance. Enum storage and bitfields
remain outside this packet; original C++03 enum semantics are unchanged.

The clean gate passed all 1422 units and 15 explicitly selected IT in 09:54,
with zero failures, errors or skips, including 44 fresh native records.
Independent 11-unit/two-IT checks overlap and are not summed. Full Maven and
exact-head CI remain separate pending gates. offsetof remains open/partial; totals stay
97 bounded obligations / 90 names and five open obligations / five names.
[Current evidence and gates](cpp03-member-pointer-layout-verification.md).
Previous sections are historical stages. #92 and #48 remain open.

# C++03 questions and decisions

[Implementation plan and acceptance criteria](CPP03-PLAN.md).
Decisions retain their original numbers; examples clarify the agreement. Update
this log and the checklist at least once per three questions. Separate open
questions from adopted decisions.

This English block covers decisions **1–73**. The
[remaining decisions and implementation notes](CPP03-DECISIONS.ru.md)
start at decision 74 and belong to [#233](https://github.com/kniazkov/code-property-graph/issues/233).
The Russian source remains intact until that migration finishes; this is not a
second independently maintained translation. Historical decisions describe agreed
behaviour, not proof of current implementation or conformance.

## Starting point

The context review found a handwritten Java/C++ parser producing an immutable
`Project`, flow analysis, generation, end-to-end tests and a bilingual model guide.
ISO/IEC 14882:2003 was selected next. Historical reference N1638 (April 2004)
contains later corrections; disputed rules require checking against 2003.
The original assignment requested a detailed Russian root Markdown checklist and
one-question-at-a-time discussion. That language requirement is historical:
[the current policy](docs/documentation-migration/README.md#language-and-branch-policy)
requires English technical documentation, with bilingual introductory root READMEs.

## 1–5. Standard library and preprocessing

1. **Library depth:** model observable operations. `std::vector<int>` can become a dynamic integer array without modelling reallocation details.
2. **Greening:** `v.push_back(x)` becomes a common append operation that reads its argument and modifies the container.
3. **Installed STL headers:** unnecessary. CPG supplies standard-based type/operation descriptions. A similarly named user method does not justify greening.
4. **No green equivalent:** retain a C++ call referencing the selected declaration, available for analysis and regeneration.
5. **User headers:** use CPG's Java C++03 preprocessor with the build's search paths, macros and conditions.

## 6–11. Macro provenance and build variants

6. **Provenance:** `SQUARE(a)` produces multiplication with two read children originating from one argument. Expose the invocation, definition operator and complete nested expansion chain. Visitors traverse children; provenance is separate, optionally overlaid in SVG.
7. **Edited expansion:** restore an unchanged invocation only if expansion is equivalent. Changing its right read to `b` emits `a * b` there, retaining provenance, definition and other invocations.
8. **Per-file configuration:** allow different settings per `.cpp` within one `Project`; one physical `config.h` may yield different typedef types per translation unit.
9. **Compilation database:** require CLI/API import of `compile_commands.json`, respecting working directory, files, header paths and preprocessing-option order. Retain folder-based invocation.
10. **Multiple commands:** keep separate variants for one `.cpp` in one project; analyses select a unit without mixing references or flows.
11. **Build membership:** specify it explicitly for cross-build call resolution. With unknown membership and multiple definitions, expose possible targets. Explain green/common nodes, red/language nodes and greening as a separate pass at the plan's start.

## 12–19. Types and flow analysis

12. **Widths:** use parameters or a profile file, such as JSON; defaults are 32-bit `int`, 64-bit `long`. The effective profile determines concrete properties.
13. **Original types:** retain C++ types and selected functions after greening. Equal-width `int`/`long` still differ in overload resolution; preserve `f(1L)` semantics.
14. **Unknown size:** alignment also has settings/defaults. Fold only with sufficient information; unknown field layout leaves `sizeof` unevaluated.
15. **Array bounds:** retain `char buf[sizeof(S) + 1]` as an operand expression; reanalysis with a complete profile can determine its length.
16. **Destruction:** CFG exposes implicit destructors on scope exit/unwinding, in reverse order for successfully constructed objects. Generated automatic destruction stays implicit.
17. **External calls:** `void work();` may return or throw. For `void work() throw();`, an exception from that call does not reach the caller's handler.
18. **External mutable reference:** after `x = 1; update(x);`, reads may originate from initialization or a call write. Passing a reference does not prove overwrite.
19. **Available body:** if `update` unconditionally writes `x = 7`, only that write reaches reads after normal return. Further DFG design was delegated.

## 20–26. Verification, editions and generation

20. **First milestone:** a reproducible example with two `.cpp` files, shared header, compilation database, macro expansion/provenance viewing, editing and execution. The main goal is a large CI E2E corpus with manually inspectable scenario sources.
21. **Compilers:** require Linux GCC and Clang in strict C++03 mode; run original and generated executable scenarios with both.
22. **Java output:** retain only simple prototype regressions. New constructs, including `vector`, are accepted through graph checks and C++03 generation.
23. **Extensions:** diagnose active unsupported constructs, e.g. GNU `packed`, with coordinates. Defer dialect support.
24. **Inactive branches:** preserve client text/directives when editing the server branch. Expose the C++ edition in parser/CLI options; this stage supports 03.
25. **Default edition:** existing API/CLI calls default to C++03; explicitly requesting an unsupported edition fails.
26. **Edition precedence:** explicit API/CLI choice, then the unit command's `-std`, then C++03. Deliberately checking newer-edition database input as C++03 is allowed.

## 27–34. Source branches, external data and templates

27. **Conflicting configurations:** editing a shared physical `return 1;` only for the server blocks generation before writes, identifying the line and both builds. Identical edits succeed; existing `#ifdef` branches can be edited independently. Never invent a condition.
28. **Inactive text:** API/JSON retain text, coordinates and inactivity per inclusion; no expansion, AST, CFG or DFG. Active branches can be expression fragments: parse the entire active unit stream. Preprocessing checks directive nesting and closing `#endif`.
29. **Unused macros:** expose active header `#define` name, optional parameters, replacement text, coordinates and possibly empty expansion list. Distinguish definitions across `#undef`/redefinition.
30. **Presence checks:** `-DDEBUG=0` makes `#ifdef DEBUG` active: one check, no expansion. Retain negative checks for absent names.
31. **External macros:** `-D` originates from a particular command argument; built-ins from a profile. External nodes remain structurally reachable through `Project` children; provenance references those nodes.
32. **Library catalogue:** keep the full catalogue outside the graph. Materialize used headers, declarations, dependencies and owners once per project. Including `vector` references its header without importing every method.
33. **Specializations:** built-ins retain a generic declaration and demanded specializations/members. With `vector<int>` and `vector<double>`, using only integer `push_back` materializes that concrete method only. Preserve explicitly written user specializations as source.
34. **Generic bodies:** expose the return/call in `template<class T> int count(const T& x) { return x.size(); }` before instantiation, leaving the dependent target unresolved. For `vector<int>`, link the specialized call to its `size()`, retaining generic-body access.

## 35–36. Instantiation reasons

35. **Requests:** distinguish call-target links from links to a required template instance. Retrieve every requesting site from a shared instance.
36. **Nested requests:** in `main → outer<int> → inner<int>`, the immediate request for `inner<int>` belongs inside concrete `outer<int>`; reverse traversal reaches the original request.

## 37–39. Diagnostics and implicit operations

37. **Instantiation errors:** first report expression coordinates and the reason with substituted types; then request sites, nearest first. Diagnostics remain available without a graph.
38. **Conversions:** explicit/implicit conversion nodes expose source/target types and explicitness. Explicit casts retain their written source range.
39. **Default arguments:** the declaration parameter owns the expression; an omitted call argument references it. Explicit arguments belong to the call. Preserve `log()` versus `log(2)` in generation.

## 40–42. Virtual methods

40. **Virtual/default dispatch:** separately link the statically selected declaration, default expression and possible executed implementation. Calls through `Base&` and `Derived` can pass different defaults to one object's method.
41. **Overrides:** link overriding methods to base declarations even without calls, with reverse lookup. Calls retain their own links. Same-name methods with different parameters are not automatically overrides.
42. **Multiple bases:** retain both `Device::reset` overrides of `Printer::reset` and `Scanner::reset`. Calls through each base reference its statically selected declaration.

## 43–45. Name-resolution chains

43. **Typedefs:** expose all declarations in `OrderId → Counter → unsigned long` and a final-type query. Preserve original spelling during generation.
44. **Using declarations:** include them in the name-to-declaration-to-type chain. API queries traverse that chain to compute the final type; no extra direct edge is required.
45. **Using directives:** `using namespace shop;` has one node referencing `shop`. Each use's resolution step links the directive to the declaration found for that name.

## 46–48. Incomplete types and comments

46. **Forward declarations:** resolve a use to the declaration visible there, then its definition if available in the project. Check completeness at the use site.
47. **Comments:** preserve text/coordinates in graph and output. Provide a separately tested Java API utility in the utilities package, returning a new snapshot without comments; no automatic execution or dedicated CLI command.
48. **Inactive comments:** that utility also cleans retained inactive text, preserving literals and token separation.

## 49–51. Comment attachment and Doxygen

49. **Ownership:** a file owns the single comment node; the explained construct references it through `HAS_COMMENT`.
50. **Attachment:** trailing comments attach to the line's last completed construct; preceding blocks attach to the next construct unless separated by a blank line. In `f(a, /* in bytes */ size)`, attach to `size`. Detached headers/ambiguous comments remain file-owned.
51. **Doxygen:** retain all text. Structurally parse `brief`, `param`, `tparam`, `return`, with `@` or `\` prefixes. Link documentation to declarations and parameter entries to function/template parameters; preserve other commands as text.

## 52–54. Documentation parameter names

52. **Stale tags:** preserve unmatched `@param` entries without links, warn at the name and return the full graph. Never infer tag targets by position.
53. **Rename:** update names in linked tags during generation; preserve description text verbatim, including old-name occurrences.
54. **Different declaration names:** attach tags to the documented declaration's parameters. Match parameters across function declarations by position. Definition parameters can access header documentation; local definition renaming leaves header tags unchanged.

## 55–57. Function documentation and unambiguous output

55. **Header/source documentation:** retain comments at their declarations; combined queries return each text with coordinates, including multiple `@brief` entries.
56. **Repeated inclusion:** return a shared-header comment once per physical location. Attachment and parameter checks depend on inclusion context.
57. **Ambiguous output:** never generate it. For known targets, retain unambiguous spelling or choose equivalent qualification. If meaning cannot remain unambiguous, diagnose and stop before writing files.

## 58–60. Renaming and erroneous graphs

58. **Uses:** rename the selected entity and linked uses project-wide, including using declarations; preserve unrelated namesakes.
59. **Overrides:** expose and rename the whole affected family, including multiple bases and calls. Diagnose blocked external-method edits before partially changing the family.
60. **Conflicts:** retain edits producing duplicate signatures in a new graph with both methods, diagnostics and root `hasErrors`. Parsing the same conflict behaves identically. Allow inspection/repair; decision 57 forbids erroneous C++ generation.

## 61–63. Parser recovery and export

61. **Syntax errors:** mark the root; retain diagnostics and an error node where locatable. Resume only at a reliable boundary; otherwise retain the parsed portion and stopping position.
62. **Other units:** parse each with its own settings even after another unit was only partially parsed.
63. **CLI JSON:** `--json` writes graph/diagnostics, including successful units, and returns nonzero. C++ generation stays blocked.

## 64–66. Viewing, analysing and repairing errors

64. **SVG:** export the available graph, mark root errors and show the stopping position for interrupted files.
65. **Flow analysis:** build CFG/DFG for completely parsed functions, including those before an error in the same file. Retain interrupted-function nodes and explicitly mark incomplete flow analysis.
66. **Repair:** automatically recheck the new snapshot after editing. Removing the last error enables C++ output; warnings remain nonblocking. Earlier snapshots retain their status.

## 67–69. Error ranges and token provenance

67. **Error nodes:** skipped fragments retain exact text/range; missing fragments have empty text and an insertion point, e.g. before `;` in `int x = ;`. Expose through API/JSON.
68. **Macro diagnostics:** for `ASSIGN(total);` expanding to `total = ;`, show invocation and definition provenance of `=`. The missing-expression node remains before `;`.
69. **Multiple files:** failed `JOIN(+, *)` token pasting points primarily to the invocation, with labelled ranges for `+`, `*`, `##` in the definition. CLI groups one message; API/JSON expose every range.

## 70. Diagnostic summaries and block completion

70. **Repeated header errors:** summarize one entry with the affected-unit count. API/JSON retain each occurrence's configuration/include chain. Different causes on one line form separate groups.

Further error handling was delegated: deterministic diagnostic order, suppression
only of provably derivative messages, and mandatory recovery progress or stopping.
Criteria/checks entered the checklist; this discussion block was complete.

## 71–73. Operator calls and container copying

71. **Overloaded operators:** retain operator syntax, operands and the selected-function link; analyse that function's behaviour. `+` alone does not justify numeric-addition greening.
72. **Element copying:** green append separately links the invoked user copy constructor and accounts for its effects.
73. **Vector copying:** create a distinct dynamic array with copied elements and their copy-constructor link. Resizing the copy leaves the original unchanged; element-type behaviour determines sharing/ownership of pointed-to objects.

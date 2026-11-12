# Bounded C++03 setjmp backend (#92)

## Contract and residual obligations

This package selects `setjmp={"emission":"gcc-clang-glibc"}` with
`cRuntime="glibc"`. It is not an inferred host capability or a portable
implementation of nonlocal transfer. Both `<csetjmp>` and `<setjmp.h>` supply
CPG-owned function-like macro definitions with recorded provenance. The selected
backend uses glibc's `_setjmp` entry directly at the save site. It does not use
`__builtin_setjmp`, invent a wrapper activation, or fabricate the representation
of `jmp_buf`. Native headers supply runtime declarations and storage.

`SetjmpExpression` owns the buffer operand and has an unknown `int` rvalue
result. Its buffer must be a directly named, standard-bound, uninitialized,
automatic local `jmp_buf` in the owning function. Qualified, reference, member,
indirect, escaped and nonlocal buffer forms are outside this bounded subset.
Declarations that conflict with `_setjmp` in the same translation unit, including
local shadowing in the save-owning function, are conservatively unsupported.
Although CPG's qualified replacement avoids local capture, unchanged generation
may preserve the original macro invocation and the native glibc macro uses an
unqualified entry name. This boundary prevents a false source-roundtrip claim.
Opaque size/alignment remain unknown. Copy/edit/JSON/source paths preserve
bindings and the operation; constant folding must not turn an initial zero
return into a universal result or erase later returns.

C90's permitted invocation contexts are checked: the entire selection/iteration
condition, a comparison with an integral constant expression that is the entire
condition, unary `!` as the entire condition, or an entire expression statement
optionally converted to void. Rejection outside this scope is CPG's diagnostic
policy, not a claim that the original standard mandates compile-time diagnosis.
General dynamic matching of save/restore environments, lifetime validity,
indeterminate changed nonvolatile locals, signal handling and interprocedural
state reconstruction are not proved.

Evaluated saves, standard-bound `longjmp` and known transitive callers have
explicitly incomplete CFG/DFG, with no fabricated ordinary-return or exception
unwinding edges. Unrelated functions retain their analyses. Known dependency propagation includes declaration chains, initialized function
pointers, default arguments, construction/destruction, allocation/deletion and
known virtual destructor targets. Constructor syntax lacking a selected binding
uses conservative arity-compatible candidates, not a claim of exact overload
resolution. Omitted constructor defaults are inspected at their construction
sites rather than treating the constructor body as unconditionally executing
them. Unknown indirect calls and external callbacks retain the existing general
analysis limitations;
this is not whole-program nonlocal-control completeness.

All five macro obligations (`offsetof`, `setjmp`, `va_start`, `va_arg`, `va_end`)
and issues #92/#48 remain open. The package is a selected executable slice,
not full C++03 conformance or full setjmp lifecycle analysis.

## Original normative basis and backend evidence

Fresh review used original ISO/IEC 14882:2003, second edition, 2003-10-15,
§18.7 table 20 and paragraphs 2 and 4 (printed 352–353 / PDF 380–381).
SHA-256: `6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.
The inherited ISO/IEC 9899:1990 §7.6–7.6.2.1 was read at printed 118–119 /
PDF 128–129: [source](https://www.yodaiken.com/wp-content/uploads/2021/05/ansi-iso-9899-1990-1.pdf),
SHA-256 `26c6a994c7e4757ab1e87cad871d88c63b4ed60f7bfd9df773f6afc329db5db7`.
No private PDF or extracted full text is included in the repository.

The original C++03 paragraph 4 speaks of **any automatic objects** destroyed
by a hypothetical exception transfer. This package does not substitute later
nontrivial-destructor wording. Positive executions must avoid new automatic
objects in skipped scopes/frames and use only well-defined retained state.
C90 requires nonzero resumed results, including conversion of a zero longjmp
argument to one, and leaves changed nonvolatile automatic locals indeterminate.

The installed glibc 2.41 `setjmp.h` separately supplies implementation evidence
(header SHA-256 `faf5d75f63557d63e0407547aa69dbf2a0a510cd5aeb1a41d12532b2e7855645`):
`setjmp(env)` maps to `_setjmp(env)` and the native runtime declares that entry.
GCC 14.2 and Clang 19.1.7 recognize its special return semantics; Clang IR marks
it `returns_twice`. These are bounded backend facts, not normative C++03 rules
or evidence for other C libraries, architectures or compiler versions.

## Verification status

The latest frozen focused suite passed 26 unit tests (six model, eight runtime
and twelve nonlocal-flow tests), zero failures/errors/skips. Earlier checkpoints
exposed implicit-conversion placement and inherited/implicit virtual destructor
closure gaps; these were fixed and covered by regressions. The frozen packaged rerun passed two integration tests, zero failures/errors/
skips; all 64 native evidence hashes were verified. Independent review passed
ten separately authored semantic/flow probes after the two virtual-destruction
findings were fixed. No full Maven/CI result is claimed yet. The native matrix has 64 executions: four include permutations ×
eight routes × GCC/Clang, with 64 recorded source hashes. Real graph edits
rebind both save/restore operands from `env` to `spare` and change a call value
from 7 to 9, changing `nonzero=12` to `nonzero=14`; zero-argument resumption and
discarded-return cases yield `zero=6` and `discarded=3`.
Acceptance records API/CLI, snapshot copy/edit/JSON/fold, rejected
boundaries and strict `-std=c++03 -pedantic-errors -O2` GCC/Clang executions of
original, generated and CPG-materialized inputs. Materialized inputs replace
the host macro with the actual recorded CPG replacement. Inputs, hashes,
commands, compiler identities and outputs belong in `target/setjmp-runtime-oracle/`.

Full clean `mvn verify`, all workflow gates, independent review, exact published
Linux/Windows head CI and post-merge master CI are separate completion gates.

Inventory checks at the implementation checkpoint passed 35 macro mutation
tests (102 obligations, five open), nine source-inventory tests, 13 paragraph
tests, ten portable-path tests and seven compiler-bootstrap tests. The complete
paragraph validator checked 5,250 records with zero errors; all source and
paragraph hashes and existing classifications were compared to the base and
preserved. Only three paragraph-record lines were changed. Tracked-path
validation includes the eight new files (845 paths). These checks do not
substitute for Java/native tests or final aggregate gates.

Frozen focused JAR SHA-256:
`91a72e7d2ec626d0b3e7156aaaa6b0394f5ea2a2a4ced5207ee8ce959be7b4a6`.
This is the focused checkpoint artifact, not a substitute for the subsequent
clean aggregate build. Independent reviewed ControlFlow source SHA-256:
`300d6a4c62abcc22bbc29735a5774b9ff1b0edcb734d7f47652e8b321b601809`.

The first clean aggregate attempt reached 1,606 unit tests and stopped on one
stale preexisting assertion that required `setjmp` to be absent. The regression
now requires a function-like unavailable definition with an empty replacement
under the default profile, matching the explicit-capability contract; later-C
macros remain absent. All 34 focused tests (including the eight original macro
tests) passed after this test-only correction. The earlier aggregate attempt
is a failure, not a successful full verification; a fresh clean run is required.

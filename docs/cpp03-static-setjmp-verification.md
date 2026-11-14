# Direct static-storage jmp_buf: bounded v2 contract

Related work: #147 remains partial/open; #146 remains open and #145 is unchanged.

## Explicit selection

Select `setjmp: {"emission": "gcc-clang-glibc-static-v2"}` with
`cRuntime: "glibc"`. The existing `gcc-clang-glibc` policy retains its
original automatic-only save admission. Profile identity distinguishes the
policies and round-trips through settings/JSON. Neither policy guesses the
representation, size or alignment of `jmp_buf`: native glibc headers own it.
The replacement remains direct `::_setjmp(env)`, not a wrapper activation.
This is a storage-admission version, not a new binary layout contract or a
claim of universal ABI support. Native evidence is the recorded host's
GCC/Clang + glibc toolchain.

## Admitted and excluded forms

V2 admits directly named, genuine unqualified standard-bound `jmp_buf`
definitions in the same translation unit: existing automatic storage,
function-local static storage, and namespace/global definitions including
internal linkage. Typedef aliases are allowed. Explicit initializers,
extern/repeated declarations, class members, references, indirect operands,
copying and escaping stay outside this package. A standard-library `longjmp`
restore must name a same-TU genuine buffer definition; operand spelling or a
user function named `longjmp` does not establish that identity. Uses are
checked across the TU, including a separate helper using a global buffer.

The implementation does not infer that a save has executed or that its
activation is alive. Restore-only syntax is not proof of a valid runtime
program. Static buffer lifetime is distinct from saving-frame lifetime;
recursion/reentrancy, overwritten environments, threading and dynamic
save/restore matching remain unproved. These are not universally ill-formed
programs merely because no whole-program proof is available.

C90 invocation-context restrictions and the original C++03 restriction on
skipped automatic objects are retained. They are not replaced with a later
nontrivial-destructor-only rule. Unknown nonlocal flow stays conservative:
no invented resumption CFG/DFG edges. `setjmp` remains nonconstant and
returns-twice; ordinary optimizations cannot erase its runtime operation.

## Evidence and gates

`StaticSetjmpTest` exercises explicit profiles, storage/identity, copies,
folding, opaque facts, source-context isolation and defensive diagnostics.
`StaticSetjmpRuntimeIT` uses both legacy headers/orders, API/CLI JSON,
GREEN/FOLDED, copies, real save/restore binding and value edits, reversal and
reparse. Original, generated and actual recorded macro-materialized inputs
are compiled with GCC and Clang using `-std=c++03 -pedantic-errors -O2`.
Independent output expectations cover zero-to-one and nonzero jump results.
Metadata, compiler identities and SHA256 hashes are retained under
`target/static-setjmp-runtime-oracle/`.

Native programs keep the saving activation alive, use static observable
state and do not jump over C++03 automatic declarations. Unsupported or
undefined examples are diagnostic-only and are never executed natively.

Local focused tests, full clean `mvn verify`, catalogue/corpus/evidence,
strict JavaDoc/demo, exact-head remote CI and post-merge master CI are
separate gates. This preparation does not claim full acceptance or a remote
pass. Remaining reference/indirect/member/escaped forms retain #147 as the
concrete open work item; the five macro obligations and #48 stay open.

### Local preparation checkpoint (2026-11-14)

Focused Maven verify passed: 32 unit tests and 4 integration tests (new and
existing setjmp runtime suites), zero failures/errors/skips. New native suite
exercised all four header variants; original fixture expectations and recorded
macro replay passed GCC/Clang at `-O2`. Independent static review found no
remaining blocking issue after namespace/field/redeclaration/restore guards
were checked. Macro inventory tests: 36 passed, obligation counts unchanged.
Full clean verify, other full CI gates, remote CI and post-merge checks are
not claimed by this checkpoint.

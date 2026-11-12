# Bounded C++03 variable-argument macros (#92)

The subsequent [typed-pointer follow-up](cpp03-typed-pointer-varargs-verification.md)
extends the bounded type subset and distinguishes unevaluated type-only checks
from runtime list restrictions. Execution results below belong to this original
package and are not results for that follow-up.

## Contract and remaining work

This package supplies a selected partial implementation, not completion of
`va_start`, `va_arg` or `va_end`. Together with `offsetof` and `setjmp`, all five
macro obligations remain **open** in the closed-schema inventory. Issues #92
and #48 and full C++03 acceptance remain open.

`PlatformProfile.varargs` explicitly selects
`{"emission":"gcc-clang-builtin","abi":"x86_64-sysv-lp64-v1"}`.
The empty/default setting is unavailable, not a host probe or inferred ABI.
Selection requires unsigned-long size_t and validates scalar sizes/alignments, including binary80 `long double` in 128-bit
storage. It is not an ABI-independent varargs implementation. Both CPG-owned
`stdarg.h` and `cstdarg` expose function macros mapped to the selected compiler
builtins. `OpaqueLibraryType` identity `library::va_list` retains UNKNOWN size
and alignment: no invented pointer, array or record layout is exported.

The supported operand is a direct local, nonconst/nonvolatile, standard-bound
`va_list` in the current variadic function. Name spelling alone is insufficient.
`va_start` requires the actual last named parameter. Retrieval supports `int`,
`unsigned int`, `long`, `unsigned long`, `double`, `long double` and object
pointers, including bound aliases. `float`, `char`, `short` and `bool` retrieval
is rejected rather than silently promoted. Reference, array, function and
promotion-incompatible final parameter types are rejected. A C++03 `register`
final parameter is not rejected merely for its storage class.

Copied/escaped lists, nonlocal, indirect and member lists, other ABIs and more
complex type cases remain unsupported. This package does not prove general
runtime argument count, actual/requested type agreement or complete lifecycle
correctness. It does not add a required `va_copy`, or import later-C mismatch
exceptions. Invalid cases are diagnostic checks, never native UB executions.

## Model and analysis boundary

`VaListExpression` is operand-bearing: kind START/ARG/END, a list `Expression`,
an optional binding-only last-parameter `Expression`, and an optional requested
`TypeNode`. START requires the last parameter, ARG the type, END neither.
The type operand is not evaluated and the last parameter is not a runtime read.
Copy/edit/visitor/JSON/generation paths preserve these operands rather than
flattening the operation into an invented ordinary function call.

START writes state; ARG reads and advances it; END reads and completes it.
An operation inside `sizeof` has no runtime effect. Folding retains the
`sizeof(va_arg(ap,T))` type dependency so a later graph edit of T recomputes its
size; it does not replace that syntax with a stale literal. Pointer retrieval keeps
alias information conservative. These effects do not constitute a dynamic
count/type/lifecycle proof or precise representation of ABI-internal storage.

## Normative review basis

The implementation executor reread original ISO/IEC 14882:2003 §18.7 table 19,
p2–3 (PDF 380–381 / printed 352–353) and §5.2.2 p7 (PDF 97–98 / printed 69–70).
The repository source identity remains
`6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.
The inherited C90 §7.8–7.8.1.3 source review covers PDF 132–133 / printed
122–123; source SHA-256 is
`26c6a994c7e4757ab1e87cad871d88c63b4ed60f7bfd9df773f6afc329db5db7`.
C++03 replaces the inherited C `va_start` restrictions; the C `register`
exclusion must not be carried over. No later-standard rule is assumed.
Only source coordinates and paraphrased scope are published, not private PDF
locators, page copies or extracted source text.

Ledger annotations retain source/paragraph hashes and existing classifications.
They record bounded package evidence, not whole-paragraph certification.

## Evidence and gates

- `analysis/VarargsCapabilityTest` covers capability selection and ABI rejection.
- `VarargsEffectsTest` covers the selected model's state effects.
- `analysis/VarargsRuntimeTest` is the focused model/resolution/edit/JSON suite.
- `e2e/VarargsRuntimeIT` is the native and packaged-CLI integration suite.

Native evidence belongs under `target/varargs-runtime-oracle/`: actual compiler
identity/version, exact original/generated/CPG-materialized inputs, hashes and
outputs for GCC and Clang in strict C++03 mode. Positive native cases must be
well-defined; rejected inputs must not be run to observe undefined behavior.
Windows runs focused Java checks, not a claim of Windows varargs ABI support.

**Focused execution:** 16 unit tests and one integration test passed. The
integration test executes 64 strict C++03 GCC/Clang original, generated, copied,
green/folded, CPG-materialized and graph-edited variants. Results include mixed=16,
counted/restarted=11, independent lists=7, wide scalars=9, second TU=15 and
edited extraction=42 (original=41). Both compilers select the const-reference
overload for the scalar va_arg rvalue. GCC emits an overbroad C90-style
`-Wvarargs` warning for the C++03-valid register final parameter; the original
2003 replacement rule and Clang oracle are retained.

**Final aggregate gates:** full clean verify, catalogue/corpus, strict private
JavaDoc/demo and published-head Linux/Windows CI are pending final executor
verification. No successful full-suite or CI run is claimed here. The executor records final results and
links in the implementation PR after freezing the source. Focused checks and
inventory validation cannot substitute for those separate gates.

Documentation/schema checks executed for this package: macro inventory validator
(102 obligations, 95 names, five open), 33 macro mutation tests, paragraph
validator (5,250 records, zero errors), 13 paragraph tests, nine source-inventory
tests and ten portable-path tests passed; tracked portable-path check passed.
These checks do not execute the Java or native implementation.

# Bounded borrowed va_list helpers (#146)

## Explicit contract

`varargs={"emission":"gcc-clang-builtin","abi":"x86_64-sysv-lp64-borrowed-v2"}`
opts into a bounded helper contract. Omitted capability and `x86_64-sysv-lp64-v1`
retain their identities and behavior. The new identity preserves existing LP64
storage/floating-format validation and compiler-owned macro replacements.
The public va_list type remains opaque: no generic size, alignment, register
layout or cursor value is invented in the graph.

The caller owns an unqualified, uninitialized direct automatic standard-bound
va_list. In the same block it must execute adjacent va_start, a direct helper call
(expression statement or variable initializer), and va_end for that list. The
call binds a visible same-TU definition of a nonvariadic, non-template free
function with exactly one genuine unqualified va_list parameter. Defaults,
indirect calls, repeated occurrences of a list argument and cross-TU assumptions
are not admitted. Aliases of the standard type work, not lookalike types.

The helper may va_arg its parameter using the existing scalar/typed-pointer
subset, but never start/end, copy, return, address, indirectly access or forward
it. Unevaluated operands remain separate. Unsupported valid forms are not called
invalid C++03. This narrow syntactic admission rule is not a proof of runtime
argument count/type matching, exception cleanup or all-path lifecycle correctness.
New admission failures use `unsupported-varargs-*` subset diagnostics. Existing
v1 `invalid-varargs-promoted-argument-type` diagnostics are preserved for API
compatibility, not reinterpreted as universal standard ill-formedness: retrieving
a type incompatible with the actual default-promoted argument can be undefined
behavior. Such negative inputs are diagnostic-only, never native tests.

## Primary basis and memory effects

Original C90 §7.8, inherited by C++03 §18.7, requires a caller whose passed ap
was consumed by va_arg in another function to pass that indeterminate ap to
va_end before any further reference. Physical cursor advancement does not permit
further caller retrieval. Each va_start remains paired with va_end in its owner.
C90 source: https://www.yodaiken.com/wp-content/uploads/2021/05/ansi-iso-9899-1990-1.pdf
The repository's existing source hash is unchanged. C++03 references use the
existing reviewed ledger, not a claimed fresh full-standard re-review.

AMD64 psABI draft0.99.6 §3.5.7 pp.52–53 specifies a one-element array of cursor
records for va_list. Array-parameter adjustment aliases the passed state.
Source: https://refspecs.linuxfoundation.org/elf/x86_64-abi-0.99.pdf
SHA256: `69e117a13b560b1b957c15f05a61b130ca56828f05dca951e27cb8e2ada14283`.
The analysis-only memory binding shares the caller cursor with the helper;
unknown/incomplete calls conservatively read/write it. This does not convert
the opaque AST type into a universally sized pointer or assert helper purity.

## Remaining scope

Reference/pointer/member/escaped operands, forwarding borrowed lists, separate-TU
or unresolved helpers, additional storage forms, POD aggregate/member-pointer
retrieval remain outside this package under #146. All five macro obligations
retain partial/open status and final acceptance #48 is not closed. No change to
closed #145 is included. No va_copy requirement is introduced.

## Evidence and separate gates

BorrowedVarargsTest covers identity, v1 rejection, binding and opaque layout,
cursor dataflow, copies, GREEN/FOLDED, edits/reversal/reparse, cross-TU/profile
isolation, and global/member/default/indirect/forwarding/lifecycle negatives.
BorrowedVarargsRuntimeIT checks strict GCC+Clang C++03 `-pedantic-errors -O2`
original/generated/API/CLI/copy/transforms and actual graph-recorded macro
materialization. Defined positives exercise register/overflow retrieval with
independent expected output. Negative lifecycle cases are never executed native.
Versions, commands, output and source hashes are retained under
`target/borrowed-varargs-runtime-oracle`, uploaded by CI.

Focused verification, full clean mvn verify, exact-head CI and post-merge master
CI are separate gates. Only executed results and JAR hashes may be recorded in
the PR; this document does not predeclare those gates green.

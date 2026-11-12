# Typed pointer varargs: bounded C++03 #92 follow-up

## Contract

This package extends the selected `gcc-clang-builtin` /
`x86_64-sysv-lp64-v1` capability, not general C++03 varargs support.
`offsetof`, `setjmp`, `va_start`, `va_arg` and `va_end` remain **open**;
#92/#48 remain open. Inventory totals and obligation classifications do not change.
`library::va_list` remains opaque with UNKNOWN size and alignment. No host
ABI inference or new pointer, enum or list layout facts are introduced.

The bounded additions are ordinary nonmember function pointers and pointers to
fixed positive-bound arrays, requested through written, bound typedef names,
including qualified names and alias chains. Named fixed bounds resolve through
`IntegralConstants` in the owning snapshot context; cyclic, dependent and foreign
types/bounds are guarded rather than guessed. Representative declarations are:

```cpp
typedef int (*Callback)(int);
typedef void (*Mutator)(int*);
typedef int (*Row)[3];
typedef const int (*ConstRow)[3];
```

`va_arg(ap, Callback)` and `va_arg(ap, Row)` preserve those types. The inherited
C90 macro contract requires that a pointer to the requested object type can be
formed by appending `*`: `Callback*` and `Row*` satisfy this constraint. Acceptance
of raw `int (*)(int)` or `int (*)[3]` by a compiler builtin is not evidence of a
portable macro-positive case. Pointer-typedef last named parameters are allowed;
raw array, function and reference last parameters remain rejected. C++03's
replacement rule does not reject a last parameter merely for being `register`.

Evaluated enum retrieval, including an enum alias, is an invalid promotion
request, just like the already rejected float and small integral requests.
This diagnostic is not an invitation to execute an undefined native call.

## Unevaluated operands and analysis

`sizeof(va_arg(ap,T))` checks type validity without runtime owner, storage or
promotion checks. Float, small scalar and complete enum types, including bound
aliases, are valid type-only requests. Direct genuine standard-bound lists can
be locals in variadic or nonvariadic functions, globals, static/extern objects,
or direct parameters in this type-only context. Qualified lists remain
unsupported; fake, foreign, unbound and cyclic types are not accepted. Reference,
member (including an implicit direct-name class member) and indirect list forms
are outside the bounded contract. Initialized/copied lists remain unsupported
at their declaration because opaque initialization is not modeled, even if a
later use is type-only. Unevaluated use does not bypass declaration validation. This does not
supply an enum layout or promise that an enum sizeof folds to a known constant.
`typeid` follows the existing C++03 non-polymorphic evaluation rules, not blanket
suppression of evaluation.

Retrieved pointers have unknown values. A callable signature does not identify
a concrete function target; alias, pointee-write and indirect-call effects must
remain conservative. Copying, graph edits, JSON, folding and generation preserve
the requested type and its dependencies, not a guessed address or stale size.
Original C++03 §8.3.5/4 rejects cv-qualification on a function type itself:
`typedef int Function(int); typedef const Function* Alias;` is a negative case.
This differs from a valid const pointer to function:
`typedef int (*Pointer)(int); typedef const Pointer Alias;`.
Modern compiler acceptance of the former reflects a later change, not the
original-2003 contract. A `QualifiedType` resolving to a function base is rejected.
Malformed function return arrays/functions, mixed void parameter lists and
array elements of void/reference/function type (also through aliases) are
rejected in evaluated and sizeof contexts. Valid void/no-argument/reference
signatures and signatures involving incomplete pointees remain covered.
Structural alias renaming within a variable declaration containing `va_arg` is
still rejected by the existing source-preservation guard. An integration
regression records that rejection. The positive editing fixture uses a separate
declaration and assignment and a dedicated `EditableCallback` alias; it does
not establish arbitrary structural rewriting.

Evaluated operations still require direct mutable standard-bound local lists
in their owning variadic function. Helpers, escaped/copied lists, member,
indirect, static and other nonlocal runtime lists remain unsupported. Member
pointers, general POD retrieval, general templates and other ABIs are not added.
No general argument-count, actual/requested-type or lifecycle proof is claimed.
There is no required C++03 `va_copy`, and no later-C mismatch exception is imported.

## Original normative basis

Normative review used ISO/IEC 14882:2003, second edition,
2003-10-15: §17.4.1.2/4–5 (PDF 353), §18.7 table 19 and /2–3
(PDF 380–381), §5.2.2/7 (PDF 97–98), §4.5/2 and §4.6 (PDF 89),
§5.3.3/1,3–4 (PDF 107), and §8.3.5/4 (PDF 167–168). Source SHA-256:
`6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.
The inherited C90 §7.8–7.8.1.3 was checked at printed 122–123 / PDF 132–133:
[public source](https://www.yodaiken.com/wp-content/uploads/2021/05/ansi-iso-9899-1990-1.pdf),
SHA-256 `26c6a994c7e4757ab1e87cad871d88c63b4ed60f7bfd9df773f6afc329db5db7`.
Compiler behavior is supplementary evidence, not a replacement for that text.
Paragraph annotations retain original hashes and classifications and do not
certify whole paragraphs. Section 11/class.access is unchanged.

## Focused verification and remaining gates

Focused evidence is recorded in:

- `analysis/TypedPointerVarargsTest.java`: type identity, aliases, promotion and
  unevaluated checks, model/copy/edit/JSON/fold behavior and rejected boundaries.
- `VarargsEffectsTest.java`: conservative pointer and indirect-call effects.
- `e2e/TypedPointerVarargsRuntimeIT.java`: well-defined strict C++03 GCC/Clang
  original/generated/CPG-materialized and packaged-CLI cases. Only typedef-based
  pointer requests count as portable positives; negatives are diagnostic-only.
  Native inputs, hashes, commands and outputs belong under
  `target/typed-pointer-varargs-runtime-oracle/`.

**Focused execution:** sequential hardened verification of the final production
function-cv guard passed 29 units, zero failures/errors/skips:
`TypedPointerVarargsTest` 11, `VarargsEffectsTest` 8, `VarargsRuntimeTest` 6 and
`VarargsCapabilityTest` 4. Packaging and `TypedPointerVarargsRuntimeIT` passed
two integration tests, zero failures/errors/skips; the final native rerun passed
72 executions with 144 source hashes. Independent review passed 43 semantic
and generation probes (12 accepted, 31 rejected), with no code blocker reported.
Earlier failed checkpoints were superseded by these final focused results;
they are not counted as passing runs. The native matrix contains
four include permutations × nine routes × GCC 14.2 / Clang 19.1.7, with 144
source hashes. Recorded output is `consume=5 callback=4 row=7 lifecycle=12
effects=37 other=13 width=12 result=4`; actual graph edits yield `width=16
result=8`. These counts describe the bounded matrix, not independent proofs of
all inherited macro requirements.

Native evidence is saved in `target/typed-pointer-varargs-runtime-oracle/`;
the CI workflow includes that path in its artifact upload. Upload configuration
is not evidence that published-head CI has executed or passed. The preceding
[varargs report](cpp03-varargs-verification.md) records its own historical checks;
those results do not certify this follow-up. Full clean verify, catalogue/corpus,
strict private JavaDoc/demo and exact published-head Linux/Windows CI remain
**pending** at freeze preparation, separate gates. Locked-tree gate results
will be recorded in the implementation PR. No successful run of those gates
is claimed here.

## Кратко по-русски

Пакет добавляет только связанные typedef-типы указателей на обычные функции и
массивы положительной фиксированной длины. Значения указателей неизвестны,
эффекты консервативны. Вычисляемое извлечение enum некорректно из-за promotion;
sizeof проверяет тип без runtime-ограничений списка и допускает float, малые
скаляры и complete enum, включая алиасы, без выдуманного layout.
Runtime-ограничения списков сохраняются. Все пять макросов и #92/#48 открыты.
Финальный прогон с function-cv guard прошёл: 29 unit-тестов, 2 IT и
72 native-запуска/144 хэша без failures/errors/skips; независимая проверка
прошла 43 semantic/generation probes (12 accepted, 31 rejected).
Full-suite и published-head CI остаются отдельными ожидающими гейтами.
Произвольное структурное переименование алиасов не заявлено: декларация с
va_arg сохраняет существующий source-preservation guard.

Documentation checks executed: macro inventory validator passed with 102
obligations, 95 names and five open obligations; paragraph validator passed with
5,250 records and zero errors. Macro mutation tests passed 33/33 after the new
test source was present (the initial run had a missing-reference failure).
Paragraph tests 13/13, source-inventory tests 9/9, portable-path tests 10/10,
the tracked portable-path check and `git diff --check` also passed. Section 11
ledger bytes were verified unchanged against HEAD. These documentation-only
results do not claim successful Java/native execution or full/CI completion.

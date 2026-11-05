# C++03 C compatibility coverage

`CCompatibilityFamiliesTest` checks all 18 C-prefixed headers and their 18
`.h` counterparts. C-prefixed declarations live in `std`; compatibility
spellings provide global declarations without inventing optional `std`
imports. `cstddef` uses the selected platform profile's size type and its
currently documented signed-size counterpart policy for `ptrdiff_t`.

The two signature-only sources check 180 declaration selections each:
formatted/variadic and positioned stdio, allocation/conversion/process/search
stdlib signatures, narrow string const overloads, every C90 classification
function and wide counterpart, three floating types for C++03 math overloads,
locale, clock/calendar, wide formatted IO, restartable multibyte conversion,
wide descriptors, signals, and nonlocal jumps. They never execute the process,
file, signal, or jump operations. They also require the relevant standard
macros to exist, without asserting implementation-specific numeric values.

The Java tests additionally exercise call resolution under both namespace
spellings, distinguish integer/floating math overloads by their selected
parameter types, reject C99/C++11 additions from the catalogue, and verify
that known declarations have no invented implementation bodies.

`c-benign-runtime.cpp` runs deterministic string, arithmetic, character,
wide-character and variadic examples only. The signature sources are compiled
but never linked or executed. GCC and Clang are checked with C++03 and
pedantic errors. On this host, Clang's GCC compatibility macros make glibc
expose legacy wide search declarations rather than the standard const
pair. Those exact `wcschr` const checks remain enabled on GCC; the standard
mutable `std::wcschr` check also runs on Clang. The legacy source suppresses
Clang's include-next extension warning because CPATH makes GCC's own limits
header appear non-system; this does not relax C++ language checks.

## Boundaries

- `cassert`, `cerrno`, `climits`, `cfloat`, `ciso646` recognition does not supply
  target macro definitions. Alternative operator tokens are C++ keywords.
- `setjmp` and `va_start`/`va_arg`/`va_end` are macros, not invented callable
  declarations. `va_copy` is not presented as a C++03 facility.
- No EOF, errno, clock scale, signal number, numeric limit or floating format
  value is guessed. Without target definitions, corresponding names remain
  unresolved in the portable graph. Native presence checks do not imply that
  the portable graph evaluates these macros.
- FILE, file position, variadic state, jump state and multibyte state remain
  opaque; clock/time types remain opaque arithmetic types and signal/wide
  integer types remain opaque integer types. No ABI layout is inferred.
- Library call resolution establishes a declaration, not purity or runtime
  behavior. Opaque-effect flow tests separately retain possible writes to
  accessible storage. Time, locale, IO, process and signal effects are not
  simulated by these coverage tests.

# Classes and initialization (#27)

The mandatory `classes-initialization` scenario compiles the original, regenerated
and folded C++03 sources with both GCC and Clang using `-std=c++03 -pedantic-errors`.
Each executable must return 0 without output.

- `Ordered` writes its initializer list as second field, first field, base. The
  observable trace must nevertheless be `1234`: base, first field, second field,
  constructor body. Leaving the scope produces `12345678`: destructor body,
  second field, first field, base destructor.
- `Copy` has observable copy-constructor and assignment counters. Both copy and
  direct initialization from an lvalue are covered without optional-copy-elision
  assumptions.
- Aggregate trailing scalar zero-fill and union first-member initialization are
  checked by execution. Separate API tests distinguish default, zero, direct,
  copy, aggregate and value initialization.
- Mixed access, private static definition, mutable member, const method and friend
  access survive source regeneration. A local static is constructed once.
- A temporary bound directly to a local const reference survives until block exit;
  an unbound temporary is destroyed at the end of its full expression.
- API checks verify original node identity, ownership, copy/rebuild, constructor
  CFG initializer ordering, implicit-special-member descriptors and lifetime
  boundaries. The packaged CLI emits equivalent JSON and valid SVG.
- `invalid/main.cpp` is C++11 direct-list initialization; parser/CLI and both
  strict native compilers reject it. JSON remains available and no source output
  is published for the invalid project.

Run `mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=classes-initialization verify`.
This is not a full ABI, overload-ranking, virtual-base or interprocedural-effects
model. Source constructors remain ordinary C++ calls at runtime; derived plans
are not expanded into guessed executable traces.

# iosfwd declaration boundary

`main.cpp` declares pointers to incomplete narrow/wide stream-buffer iterators
and character traits using only `<iosfwd>`, then includes the defining headers
and uses both traits specializations. Expected exit status: 0.

```sh
g++ -std=c++03 -pedantic-errors main.cpp -o probe
./probe
```

`IosfwdDeclarationIT` also tests the prefix before `<iterator>` with a trivial
main, so the include-only case does not accidentally depend on definitions.
Both original and CPG-generated source are compiled and run with GCC and Clang.

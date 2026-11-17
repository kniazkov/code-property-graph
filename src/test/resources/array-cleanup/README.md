# Partial array lifetime regression (#95)

`main.cpp` logs construction (+id), destruction (-id), allocation release (D),
outer guard destruction (S), constructor function-try (H) and catch entry.
`expected.txt` records twelve scenarios: success and failure of first/middle/last
array element, for automatic, new[] and member arrays. Trailing whitespace in
runtime output is ignored; token order and line boundaries are exact.

```sh
g++ -std=c++03 -pedantic-errors main.cpp -o probe
./probe
```

Use clang++ for the second oracle. ArrayCleanupIT runs original and CPG-generated
code and preserves traces in target/array-cleanup-oracle. This runtime control
uses replaceable global allocation; placement and class-specific lookup are not
claimed. See docs/cpp03-array-cleanup-verification.md for the bounded CFG contract.

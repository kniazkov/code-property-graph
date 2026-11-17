# Stream-base access control

The six `*-copy.cpp` / `*-assign.cpp` files must be rejected by CPG and by
`g++ -std=c++03 -pedantic-errors -fsyntax-only FILE` (likewise Clang).
They use ios_base, ios and wios. CPG must report inaccessible access and block
source generation, retaining diagnostic JSON.

`main.cpp` is the positive control: copyfmt is allowed and preserves width.
Compile with `g++ -std=c++03 -pedantic-errors main.cpp -o probe` and run it;
expected exit status is 0. E2E also compiles and runs CPG-generated output.

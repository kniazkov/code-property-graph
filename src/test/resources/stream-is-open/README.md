# Printed C++03 is_open

`main.cpp` queries six mutable file streams and two const file buffers, all
initially closed. Compile with `g++ -std=c++03 -pedantic-errors main.cpp -o probe`;
expected exit status: 0. E2E also compiles and runs the generated program.

The six `*-const.cpp` programs must receive CPG no-viable-overload diagnostics
under the printed profile. Modern native libraries may accept them; E2E records
compiler status/output without treating native acceptance as a CPG regression.

# Stream type properties

main.cpp is a portable narrow/wide stream control (exit 0). CPG generates it;
GCC and Clang compile and execute both original and generated variants.
invalid-enum.cpp must be rejected: an integer cannot implicitly become seekdir.
native-properties.cpp is a compiler-only oracle: numeric_limits confirms signed
integer streamsize, __is_enum confirms seekdir. The compiler intrinsic is not
fed to CPG and imposes no concrete width, enum values or ABI assumptions.

Use `g++ -std=c++03 -pedantic-errors main.cpp -o probe` and `./probe`.

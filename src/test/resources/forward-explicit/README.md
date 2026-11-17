# Forward declaration and explicit instantiation

`main.cpp` is a runnable C++03 regression: forward declaration, differently
named template parameter in the definition, explicit instantiation, then a
call reusing that instance. Expected exit status: 0.

```sh
g++ -std=c++03 -pedantic-errors main.cpp -o probe
./probe
```

[API/CLI/native verification](../../../../docs/cpp03-forward-explicit-verification.md).

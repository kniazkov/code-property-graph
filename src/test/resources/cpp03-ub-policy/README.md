# C++03 undefined-behavior policy inputs (#97)

These inputs have UB under the printed 2003 edition (§16.3/10, §16.3.3/3,
§16.4/3, §16.8/3 respectively). CPG chooses to reject them. A refusal is a
project policy regression, not a mandatory ISO diagnostic. Do not compile/run
these fixtures as native result oracles. They are outside the executable corpus.

`Cpp03UndefinedBehaviorPolicyIT` reads these same files through API and CLI,
checks diagnostic JSON and blocked generation, and copies/reanalyses the graph.
Manual inspection after `mvn package -DskipTests`:

```sh
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --cpp-standard c++03 \
  --input src/test/resources/cpp03-ub-policy/empty-argument \
  --stage resolved --json policy.json --out generated
```

Expected CLI status: 1, diagnostic JSON, no generated source directory.
Use a fresh output directory for each run. Other subdirectories exercise the
other three policies. Valid independent template specialization/instantiation
examples remain in `cpp03/template-instantiation/sources` and run under both
GCC and Clang in the ordinary corpus.

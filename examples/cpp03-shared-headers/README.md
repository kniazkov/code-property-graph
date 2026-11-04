# Edit one physical header across C++03 configurations

`EditSharedHeaders` uses the public `ProjectEditor` API to replace the integer in
`retry_limit()` from `1` to `2`. Both database records name the **same** physical
`main.cpp` and `include/common.h`, once with `-DSERVER` and once with `-DCLIENT`.
There is no graph-edit command in the CLI. The demo requests the entire
`Cpg.sources(...)` map before creating the destination, writing sources, copying
the database, or exporting graph JSON.

Reusable fixtures live in
[`src/test/resources/cpp03/shared-headers`](../../src/test/resources/cpp03/shared-headers):

- `common`: both variants use one physical `return 1;` statement. Editing only
  the server is rejected with the header path, line/column range, both variant
  IDs/configurations, and the required `2` versus `1` text. No destination is
  created. Editing both emits exactly one `return 2;` statement.
- `conditional`: the header already contains `#ifdef SERVER` / `#else` /
  `#endif`. A server-only edit succeeds. The client branch, comments, tabs,
  spacing, CRLF line endings, and surrounding directives remain byte-for-byte
  unchanged. Introducing a new condition is the user's responsibility.

## Reproduce

Run from the repository root with JDK 21, GCC, and Clang installed:

```sh
mvn verify
jar=target/cpg-0.0.1-SNAPSHOT.jar
classes=$(mktemp -d)
work=$(mktemp -d)
fixture=src/test/resources/cpp03/shared-headers
javac -cp "$jar" -d "$classes" examples/cpp03-shared-headers/EditSharedHeaders.java

# Expected nonzero status; no source or graph export is written.
if java -cp "$jar:$classes" EditSharedHeaders \
    "$fixture/common/compile_commands.json" server "$work/conflict"; then
  echo 'Expected conflict' >&2; exit 1
fi
test ! -e "$work/conflict"

java -cp "$jar:$classes" EditSharedHeaders \
  "$fixture/common/compile_commands.json" both "$work/common"
java -cp "$jar:$classes" EditSharedHeaders \
  "$fixture/conditional/compile_commands.json" server "$work/conditional"
for compiler in g++ clang++; do
  for scenario in common conditional; do
    "$compiler" -std=c++03 -pedantic-errors -DSERVER \
      "$work/$scenario/sources/main.cpp" -o "$work/server"
    "$compiler" -std=c++03 -pedantic-errors -DCLIENT \
      "$work/$scenario/sources/main.cpp" -o "$work/client"
    test "$("$work/server")" = 2
    if [ "$scenario" = common ]; then expected=2; else expected=1; fi
    test "$("$work/client")" = "$expected"
  done
done
```

`SharedHeaderGenerationIT` verifies all three editing cases and packaged-CLI
reimport of successful exports. The `shared-headers` corpus scenario compiles
and runs both original configurations (`1/1`) and both generated configurations
(`2/2` and `2/1`) with **both** GCC and Clang under
`-std=c++03 -pedantic-errors`; missing compilers fail rather than skip tests.
The same checks run in `scripts/demo.sh` alongside the existing Java/C++ demos.

Focused native acceptance run:

```sh
mvn verify -Dcpg.cpp03.scenario=shared-headers
```

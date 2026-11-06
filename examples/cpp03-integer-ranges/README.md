# Lattices and opt-in integer ranges / Решётки и диапазоны

`RunRanges.java` is an external client of the packaged CPG JAR. It implements its
own sign domain, consumes a real integer-range analysis, and exports both domains
through the same public `LatticeElement` interface without registering a codec.
It needs JDK 21. The full acceptance also requires Maven, GCC and Clang.

Run from the repository root, choosing a fresh output directory:

```sh
mvn verify -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=integer-ranges
mkdir -p target/integer-range-classes
javac -Xlint:all -Werror -cp target/cpg-0.0.1-SNAPSHOT.jar \
  -d target/integer-range-classes examples/cpp03-integer-ranges/RunRanges.java
java -cp target/cpg-0.0.1-SNAPSHOT.jar:target/integer-range-classes RunRanges \
  src/test/resources/cpp03/integer-ranges/sources target/integer-range-output
for compiler in g++ clang++; do
  for version in ordinary direct dependent; do
    "$compiler" -std=c++03 -pedantic-errors \
      "target/integer-range-output/$version/main.cpp" -o "target/integer-range-$version"
    "target/integer-range-$version" # no output, exit status 0
  done
done
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --stage resolved --cpp-standard c++03 \
  --input target/integer-range-output/dependent --json target/integer-range-reparsed.json \
  --out target/integer-range-reparsed
```

On Windows use `;` instead of `:` as the Java classpath separator. `mvn verify`
compiles this exact Java source with `-Xlint:all -Werror` using only the packaged
JAR, runs it in a separate process, independently inspects the JSON, and builds
and runs the original, all generated versions, and CLI-reparsed sources with
both native compilers in strict C++03 mode.

## Three independent runs

1. `Cpg.read(..., Stage.RESOLVED, "c++03")` performs an ordinary build. It has
   neither integer-range products nor an `IntegerRanges` completion record.
2. `new PassRunner().register(new IntegerRanges()).run(project, IntegerRanges.ID)`
   explicitly requests range analysis. Read typed values using
   `project.resultOf(expression, IntegerRanges.RANGE)`.
3. A fresh runner starts from the **ordinary** snapshot and requests only
   `example.range-checker`. Its `requires()` declares `IntegerRanges.ID`, so the
   runner prepares ranges before the checker. History proves this order and
   shows each pass exactly once.

The fixture assigns `value = 1` on one branch and `value = 10` on the other. At
`value == 5`, the range of `value` is **[1,10]**, the interval hull, not the finite
set `{1,10}`. Thus 5 remains abstractly possible, and equality has range [0,1].
The analysis does not rewrite or remove the condition. Running both real branches
still returns 1 or 10, and all generated programs preserve that behavior.

## Common domain contracts

`Lattice<E extends LatticeElement>` supplies `lessOrEqual`, `join`, `meet`,
`bottom` and `top`. `IntegerDomain` provides integer constants and inclusive
intervals. `interval(7, 7)` normalizes to `constant(7)`, including semantic equality,
hash code and identical JSON. Joining 1 and 10 yields [1,10]; intersecting [1,7]
and [7,10] yields the constant 7; disjoint constants meet at bottom.

The example's independent `SignDomain` is a powerset lattice over negative, zero
and positive. The checker uses its operations on the actual range result to
publish a positive sign. Sign values implement only the public `LatticeElement`
serialization contract. The checker publishes signs and built-in integers with
`ResultKey<LatticeElement>` and the default `PassRunner`, without a class switch,
custom encoder, reflection or stringification.

`Optional.empty()` means no result was published. It is different from a present
bottom value (the empty abstract set) and a present top value (unknown). The JSON
keeps these distinctions: the missing key has no entry, while both custom and
integer bounds have structured entries with explicit `kind` values. Constants
and intervals retain numeric values and bounds, and each result also retains its
producer, declared type, node and freshness.

The output includes `ordinary.json`, `direct.json`, `dependent.json`, an identical
repeated export, `checks.txt`, and matching `ordinary/`, `direct/`, and `dependent/`
source trees. These read-only analyses emit no custom diagnostics and preserve
the input snapshot. The ordinary CLI can reparse their sources, but does not load
the Java checker or reconstruct opt-in analysis products from source text.

## По-русски

Внешний Java-клиент компилируется только с опубликованным JAR и реализует свою
решётку знаков. Общие интерфейсы `Lattice<E>` и `LatticeElement` позволяют
использовать собственный домен в проходе и сохранять его структурированный JSON
без проверки конкретных классов и отдельного кодировщика.

Проверяются три независимых запуска: обычная сборка без диапазонов, явно
запрошенный `IntegerRanges` и пользовательская проверка, для которой диапазоны
автоматически готовятся как зависимость. История подтверждает порядок.
После присваиваний 1 и 10 диапазон равен [1,10], поэтому сравнение с 5 остаётся
абстрактно возможным. [7,7] эквивалентен константе 7. Отсутствующий результат
отличается от опубликованных bottom и top. GCC и Clang проверяют исходную,
сгенерированные и повторно разобранную CLI программы в строгом C++03.

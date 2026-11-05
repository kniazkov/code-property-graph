# Literals, operators and conversions / Литералы, операции и преобразования

`LiteralsExpressionsIT` and the nineteenth mandatory corpus scenario share
`LiteralsExpressionsAcceptance`. The positive program covers:

- C++03 decimal/octal/hexadecimal integer literals, suffixes and values up to
  unsigned-long maximum; float/double/long-double lexical values; bool, ordinary
  and wide characters, escapes and adjacent narrow/wide strings.
- Array indexing, indirection and pointer-to-member operators; prefix/postfix
  increments/decrements, every compound assignment, bitwise and logical
  operations, shift/arithmetic precedence, conditional and comma expressions.
- Short-circuit and conditional side effects, which must remain unchanged after
  resolved and folded source generation.
- A unique `accept(long)` called with `int x` and `static_cast<long>(x)`. Both
  conversions expose their source/target types, but only the latter is explicit,
  has the written cast range and generates `static_cast<long>`.
- Array decay, pointer qualification, null-pointer conversion and reference
  binding. Expression type/category metadata is separate from literal values.

The API and packaged CLI must agree on JSON and generated C++, retain snapshot
reference ownership through copying, and generate SVG. Generated sources are
reparsed and inspected again. C++-specific constructs cannot silently turn into
invalid Java. Both GCC and Clang compile the original, resolved-generated and
folded-generated source with `-std=c++03 -pedantic-errors`; all three return **0**
with no stdout or stderr. The fixture uses the default Linux-like platform
profile, including 64-bit `unsigned long` and 32-bit `wchar_t`.

The negative companion contains a C++11 binary literal. Recovering API parsing
must record an error; CLI returns 1, retains JSON/SVG and creates no source output.
Both native compilers reject it in strict C++03 mode.

```sh
mvn -Dit.test=LiteralsExpressionsIT verify
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=literals-expressions verify
mvn verify
python3 scripts/check-cpp03-reports.py
```

The full corpus has 19 scenarios and 21 report entries including its two harness
checks. Floating literal `BigDecimal` values describe their decimal spelling,
not a target-rounded floating representation. Full cast/new/delete semantics and
more complete constant folding are #28; overload ranking and user-defined
conversions are #30.

## Русский

Приёмка проверяет литералы C++03, приоритеты и ассоциативность операций,
категории выражений и стандартные преобразования через API и упакованный CLI.
Значение литерала проверяется отдельно от типа результата. У вызовов
`accept(x)` и `accept(static_cast<long>(x))` проверяются исходный и целевой типы,
признак явности и диапазон написанного приведения. Неявное преобразование
не добавляет явный cast в сгенерированный текст.

Исходный, сгенерированный после разрешения и сгенерированный после свёртки
варианты собираются GCC и Clang строго как C++03 и возвращают **0**.
Проверки короткого замыкания, условной операции, инкрементов и составного
присваивания обнаруживают потерю или лишнее выполнение побочных эффектов.
JSON, SVG, копирование и повторный разбор должны сохранять проверяемый контракт.

Отрицательный пример с двоичным литералом C++11 завершается CLI-кодом 1,
сохраняет диагностические JSON/SVG и блокирует генерацию исходников.
Нативные компиляторы также обязаны отвергнуть этот пример.

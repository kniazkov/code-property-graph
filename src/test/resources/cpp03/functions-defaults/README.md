# Functions and default arguments / Функции и аргументы по умолчанию

`FunctionsDefaultsIT` and the twentieth mandatory corpus scenario share
`FunctionsDefaultsAcceptance`. The positive source declares
`void log(int level = next());` separately from its definition, then executes
`log(); log(2); log();`. The global counter must be **2** and the accumulated
value **5**. Original, resolved-generated and folded-generated programs compile
with both GCC and Clang under `-std=c++03 -pedantic-errors` and return **0**.

The graph retains one source `next()` expression owned by the prototype's
parameter. Both omitted calls link to that same expression, parameter and visible
provider declaration. The middle call retains one explicit argument and no
default link. Ordinary child traversal sees the default expression once; it is
not cloned under either call. Data-flow VALUE edges represent each call's use.
The selected implementation remains separate from the source default provider.

The fixture also checks defaults accumulated across two same-scope prototypes,
references and array parameters, an inline function, a variadic function and
function-pointer calls through both a name and a dereference. Pointer calls do
not inherit function declaration defaults. API copying, owned-reference checks,
replacement of the shared default, JSON, SVG and generated-source reparse exercise
the public interfaces. Generation keeps the two written `log()` calls omitted
instead of expanding `next()` into the caller.

The negative companion calls `sink()` before a later declaration adds a default.
It parses but must produce a semantic error: a later default does not make an
earlier call valid. CLI returns 1, retains requested JSON/SVG, and creates no
source output. Both native compilers reject the original negative fixture.

```sh
mvn -Dit.test=FunctionsDefaultsIT verify
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=functions-defaults verify
mvn verify
python3 scripts/check-cpp03-reports.py
```

The full manifest has 20 scenarios and 22 report entries, including the two
harness checks. Full overload ranking/ADL is #30; class inheritance, virtual
selection and its default-argument interaction are #29.

## Русский

Вызовы `log(); log(2); log();` сохраняют одну написанную конструкцию `next()`
при параметре прототипа и две ссылки на неё из вызовов с пропуском аргумента.
Явный аргумент среднего вызова остаётся отдельным литералом. Цель вызова —
реализация функции — не подменяет конкретное объявление, предоставившее default.
После генерации `next()` выполняется дважды, счётчик равен **2**, сумма — **5**.

API, CLI, JSON, SVG, копирование, замена общего default-выражения и повторный
разбор проверяют принадлежность ссылок и сохранение исходной формы. Дополнительно
проверяются накопление defaults из видимых прототипов, параметры-ссылки и массивы,
inline, variadic и вызовы через указатель на функцию. Исходный и два
сгенерированных варианта собираются обоими нативными компиляторами и возвращают
**0**. Поздний default у `sink` не исправляет более ранний вызов: отрицательный
пример сохраняет диагностический граф, но блокирует генерацию.

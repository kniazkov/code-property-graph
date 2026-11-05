# Scopes, declarations and linkage / Области, объявления и связывание

`ScopesLinkageIT` and the eighteenth corpus scenario share `ScopesLinkageAcceptance`.
The positive project is read through its ordinary compilation database. Only
`a.cpp` defines `WIDE`; `a.cpp` and `b.cpp` therefore instantiate the exact same
physical `typedef VALUE_TYPE Width;` range as different types (`long` and `int`).
Their uses must point to the declaration in their own TU, including after copying,
selecting a build composition, generation and reparse.

`main.cpp` exercises reopening `shop`, a namespace alias, one shared
`using namespace shop` directive used by both `OrderId` and `Count`, an explicit
`using` declaration and a qualified type name. The unqualified `OrderId` chain
passes through the directive and the `OrderId`/`RawId` typedefs to `long`.
`shop::OrderId` skips the directive. API links and JSON use the same owned nodes;
no fabricated directive is created per use. The forward `Order` declaration is
the visible target for `current`, links to its later definition, and retains its
incomplete-at-use state. `later` is complete. The `ApiFunction alias_api;`
function declaration preserves written typedef spelling but participates in
normal signature/linkage resolution. `extern "C"` declaration and definition
refer to one function.

Each of `a.cpp` and `b.cpp` has its own file-static `helper` and unnamed-namespace
`hidden`. Block-scope `helper` and `extern offset` redeclarations inherit the
visible file-static linkage, including in a selected composition. Calls cannot bind to the other TU's internal function. The packaged CLI
must agree with the API JSON, emit the same source set, and report no semantic
errors. Strict GCC and Clang compile each original/generated TU separately with
its own flags, then link and execute it: empty stdout, exit **33**. This is not a
whole-build global `-DWIDE` invocation.

The `incomplete` companion parses successfully but contains `sizeof(Order)`
before the definition. It must have one `incomplete-type` semantic ERROR, even
though the later definition exists. `Project.hasErrors()` remains parse-only.
CLI returns 1, exports requested JSON/SVG outside the source destination and
creates no generated directory. API source emission also rejects the graph.
Both mandatory native compilers reject this fixture with `-fsyntax-only`.

```sh
mvn -Dit.test=ScopesLinkageIT verify
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=scopes-linkage verify
# Complete, unfiltered acceptance:
mvn verify
python3 scripts/check-cpp03-reports.py
```

The full manifest contains 18 scenarios; its report guard expects 20 results,
including the two infrastructure checks. Overload selection/ADL (#30), class ABI
layout (#28), and mixed access sections remain later work. Tests require accurate
supported declaration identities and source generation, not those later features.

## Русский

Приёмка сопоставляет API, упакованный CLI, JSON, копирование снимка, выбранный
состав сборки и повторный разбор сгенерированных файлов. `-DWIDE` задан только
для `a.cpp`; две версии `Width` имеют один физический диапазон в `config.h`, но
разные типы и отдельные привязки в своих единицах трансляции.

Одна директива `using namespace shop` используется в двух цепочках разрешения
типов. Цепочка `OrderId` проходит через директиву, `OrderId` и `RawId` до `long`,
а явное `shop::OrderId` не содержит лишнего шага `using`. Предварительное
объявление `Order` сохраняется отдельно от определения. Указатель `current`
связан с видимым предварительным объявлением; у более позднего `later` тип полон.
Объявление функции через typedef, `extern "C"`, повторное открытие пространства
имён и псевдоним пространства также сохраняются при генерации.

Два файловых `static helper` и две функции `hidden` в анонимных пространствах
не смешиваются между единицами. GCC/Clang собирают каждую единицу с её флагами,
затем компонуют программу: исходный и сгенерированный варианты возвращают **33**.
Отрицательный пример с ранним `sizeof(Order)` сохраняет диагностический граф,
завершается CLI-кодом 1 и блокирует генерацию. Позднее определение не исправляет
неполноту типа задним числом. Нативные компиляторы также отвергают этот пример.

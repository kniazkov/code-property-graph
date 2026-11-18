# #95 — indexed array lifetime and failure cleanup

## Русский

Один пакет добавляет CFG частичного создания для одномерных массивов классов
с вычислимой длиной 1–64: автоматические переменные, поля конструируемого объекта
и обычные `new T[N]` / `new T[N]()`. Для new[] также допустима нулевая длина.
Лимит — предохранитель размера развёрнутого графа, не правило C++03.
Основание: ранее сверенный исходный §15.2/2; повторный просмотр PDF не заявляется.

Для каждого элемента есть IMPLICIT_CONSTRUCTOR/IMPLICIT_DESTRUCTOR с objectId,
отсчитываемым от нуля elementIndex и ссылкой на объявление ctor/dtor, когда оно явно существует.
У неявного специального метода отдельного AST-объявления может не быть; ссылка
тогда отсутствует. Это не означает пропуск операции. Индекс сохраняется в JSON,
DOT и при копировании снимка. События не добавляются в дерево исходного кода.

Нормальный путь конструирует по возрастанию индекса. При отказе элемента i
разрушаются i−1…0; неуспешный элемент не разрушается. Затем new[] выполняет одно
ARRAY_DEALLOCATION, после чего ARRAY_UNWIND передаёт исключение через очистку
внешних guard-объектов в подходящий handler/границу функции. Отказ allocation
не вызывает ни конструкторов, ни освобождения невыделенной памяти. Успешный
new[] не очищается автоматически при выходе из области указателя.

Массив-поле учитывается в порядке subobjects и очищается до более ранних полей
и входа в constructor function-try handler. Второй throw из деструктора при
раскрутке ведёт в TERMINATE_EXIT. При throw из нормального разрушения очищается
оставшийся префикс. Return и вложенные try сохраняют порядок live-объектов.

DFG остаётся консервативным на уровне корня массива: конструирование пишет
aggregate root, разрушение читает его состояние. Для heap-массива корнем служит
NewExpression. Персональная идентичность элемента находится на CFG-событии;
отдельные reaching definitions для каждого runtime-элемента **не заявляются**.
Промежуточные ctor-события не заставляют DFG повторно вычислять уже исполненный
new[] при присваивании результата переменной или при return.

Границы, сохраняющие incomplete и отсутствие частичных control/data edges:
символическая/слишком большая длина, несколько измерений, aggregate initializer,
static/extern массивы в теле функции, placement/class-specific new[], new[]
внутри аргумента/условия/другого неподдержанного выражения. Модель matching
placement-delete lookup не добавляется. Прямые new[] поддержаны как initializer
переменной, expression statement и return; вне этого подмножества полнота не
обещается. Отдельное разворачивание успешного `delete[]` не добавлено: здесь
реализовано освобождение storage при неуспехе создания. Остальные исторические
границы CFG (временные объекты, virtual-base context, unexpected) сохраняются.

## English

This package expands bounded one-dimensional default-constructed class arrays
into indexed, flow-owned lifetime operations. It covers automatic storage,
member subobjects and ordinary new[], including zero-length allocation.
Failure destroys only the successfully constructed prefix in reverse order;
heap storage is released once after element cleanup and before outer unwinding.
Allocation failure and successful allocation do not take that cleanup path.
Constructor function-try handlers, surrounding guards, nested try/return and
throwing-destructor paths are checked. Snapshot copying, reanalysis, graph edits,
JSON/DOT and generation preserve the indexed operation contract without adding
explicit destructor calls to generated source.

DFG uses aggregate storage roots, not separate element-wise reaching definitions.
Unsupported shapes retain incomplete flows. The 64-element expansion budget is
an implementation limit. General dynamic-array loops, placement/custom allocation
lookup and successful delete[] element expansion are outside this package.
Paragraph 15.2/2 therefore stays partial; accepting issue #95's selected cases
must not be read as full chapter or language conformance.

## Checks

Base: master `a512105abb3f61825f5e41a8f3800155830988e3` (PR #168 merged).
The intermediate cloud run uses Java 21 and GCC; Clang remains in mandatory CI.

```sh
mvn -B -ntp -DargLine=-Xmx768m \
  -Dtest=ArrayCleanupFlowTest,ExceptionCleanupFlowTest,FlowTest,StatementExceptionExportTest,ObjectMemoryDataFlowTest,NonlocalControlFlowTest,ExpandedExpressionDataFlowTest \
  '-Dit.test=ArrayCleanupIT#gccOriginalAndGenerated' verify
```

The local environment invokes the same arguments through `python /tmp/runmvn.py`
which selects the installed JDK/Maven and supplies proxy settings. It is not a
repository dependency. Omitting the GCC method selector runs both GCC and Clang
methods. This partial command does not replace the unmodified full CI.

`ArrayCleanupFlowTest` asserts each failed index, object/declaration references,
reverse cleanup order, exactly one heap release, no release on allocation failure
or success, function-try/member lifetime, terminate, aggregate DFG, snapshot copy,
GREEN/FOLDED recomputation, an actual bound edit and reparse, zero-length/noexcept,
unevaluated allocation, nested return and unsupported shapes.

`ArrayCleanupIT` executes the CLI, exports diagnostic/flow JSON and sources, then
compiles and executes original and generated C++ with `-std=c++03 -pedantic-errors`.
Twelve exact traces cover success and first/middle/last failure in automatic,
heap and member arrays. Replacement global new[]/delete[] use malloc/free and
print D at deallocation, making release order observable. The generated source
must not add explicit destructor calls. Commands, compiler version, outputs,
source and JSON are retained in `target/array-cleanup-oracle` and CI artifacts.

Full Linux verify, Windows unit tests and Clang on the final PR revision remain
the acceptance gate. The agent does not wait for the full GitHub run.

## Recorded result — 2026-11-17

Final focused verify: **94 unit tests + 1 GCC E2E**, zero failures/errors/skips,
BUILD SUCCESS. The E2E compares all twelve traces for original and generated
code (24 observed scenario traces). The final run took 29.123 seconds.

JAR SHA-256: `e85ceb5f84bc9a59b00656d102b4111e67e47a8ef991b45a2a22a6dede073f99`.
Source/test SHA-256: `4105b69f6132eed967f8c37b867e687371a2c930278747ee32852b7b57d3afd5` (sorted pom.xml and src files,
UTF-8 relative path + NUL + file bytes + NUL). Documentation and workflow artifact
paths are outside this source hash. The source/tests did not change after this run.

Paragraph ledger, portable paths, whitespace and JavaDoc with -Werror passed.
No complete local suite or local Clang execution is claimed.

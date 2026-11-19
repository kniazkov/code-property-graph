# Automatic-object cleanup in unexpected-handler outcomes (#96)

## Русский

Анализ исходов известных `unexpected_handler` учитывает время жизни выбранных
локальных объектов: создание, выход из блока или через `return`, очистку при
исключении и обратный порядок разрушения. Если конструктор бросил исключение,
деструктор этого объекта не запускается. При сбое создания элемента массива
разрушаются только ранее созданные элементы.

Исключение из деструктора при обычном выходе из области становится новым
исключением и может попасть в окружающий `catch`. Исключение, покинувшее
деструктор во время раскрутки стека, даёт `TERMINATE_EXIT`, а не замену через
`std::bad_exception`. Если деструктор сам поймал своё исключение и завершился
нормально, исходное исключение продолжает распространение. Явное завершение
программы не превращается в исключение из последующей очистки.

Поддерживаются автоматические объекты без записанного инициализатора и
одномерные массивы с постоянным размером от 1 до 64. Класс не должен иметь баз,
полей-объектов, полей-массивов или полей-ссылок. Неявные специальные функции для
этого выбранного случая считаются завершающимися нормально. Явные конструктор
по умолчанию и деструктор требуют полного поддержанного тела, без параметров,
списка инициализаторов конструктора и function-try-block. Список инициализаторов
проверяется и у определения вне класса. Ветви объединяются без вычисления
значений переменных; одинаковые элементы массива не считаются независимыми
конкретными исполнениями.

Статические объекты, копирование, временные объекты, аргументы по умолчанию,
составные подобъекты, неизвестные тела и рекурсивные вызовы не дают оснований
для удаления вариантов исхода. Нарушение спецификации исключений внутри
специальной функции также оставляет консервативный результат. Сохраняются
ограничения восьми активных функций и бюджета 2000 посещений функций/операторов.
Это уточнение исходов CFG; точные записи деструкторов в память не заявляются.

Основание: сохранённый проект N1638 (11 апреля 2004), §15.2/1–3 и §15.5.1/1.
Повторно просмотрены правила обратного порядка очистки, частичного создания
массивов и завершения при исключении из деструктора во время раскрутки стека.
Это не сверка оригинальной печатной редакции ISO/IEC 14882:2003 и не доказательство
полного соответствия C++03. #96 остаётся открытой.

## English

Known-handler outcome summaries now model selected automatic-object lifetimes:
construction, block/return cleanup, reverse destruction and exceptional unwinding.
A failed constructor does not run that object's destructor. Partial array
construction destroys only completed elements. A destructor exception on normal
scope exit may reach an enclosing catch; one escaping during unwinding terminates,
without taking the bad_exception replacement path. Locally caught destructor
exceptions may complete cleanup normally.

The supported subset is default-initialized automatic complete objects and
one-dimensional arrays of 1–64 elements. Classes have no bases or object, array,
or reference fields. Implicit special members are normal in this subset; explicit
ones require complete supported parameterless bodies, no constructor initializer
list and no function-try-block. Out-of-class definitions are checked as well.
Static storage, copies, temporaries, default arguments, hidden subobjects,
unknown/recursive bodies and special-member exception-specification violations
retain conservative alternatives. Branch unions do not infer runtime values.
The eight-active-function and 2000-visit bounds still apply.

This refines CFG outcomes, not exact destructor memory effects. Existing concrete
handler may-effects and opaque lifetime effects remain. Nodes and JSON schema are
unchanged; snapshot copying and handler/destructor edits recompute the outcomes.
The source checked is the retained April 2004 N1638 draft, sections 15.2/1–3 and
15.5.1/1, not a fresh verification of the original published 2003 edition.

## Verification

Base: `f83cb72` (merged PR #176). Java 21; native C++03 uses GCC with
`-std=c++03 -pedantic-errors`. The focused command is the previous memory-effect
verification command with `UnexpectedHandlerLifetimeTest` added to `-Dtest`.

The new native fixtures cover normal replacement, unwinding termination, normal
scope-exit throws, failed construction, reverse cleanup order, partial array
construction, a destructor throwing during partial-array unwinding, return cleanup
and nested catch dispatch. Original and generated code run in isolated processes;
termination handlers verify observable counters before exiting. Returning
unexpected handlers and other contract violations are not positive native oracles.
Full CI, including Clang, remains required before merge.

Verified 2026-11-19: **148 unit tests, one GCC E2E test, 37 scenarios / 74
original/generated native executions**, zero failures. Strict Javadoc, portable
paths, paragraph-ledger validation and diff checks passed.

```sh
mvn -B -ntp -DargLine=-Xmx768m -Dtest=UnexpectedHandlerLifetimeTest,ExceptionMemberPointerTest,ExceptionPointerQualificationTest,ExceptionBoundaryTest,ExceptionCleanupFlowTest,ArrayCleanupFlowTest,StatementExceptionExportTest,UnexpectedHandlerMemoryTest,UnexpectedHandlerOutcomesTest,UnexpectedHandlerStateTest,UnexpectedHandlerFlowTest,ObjectMemoryDataFlowTest,ObjectMemoryExportTest,JsonExportTest -Dit.test=ExceptionBoundaryIT#gccOriginalAndGenerated verify
```

Tested JAR SHA-256: `5986fa6ad2d5c8f46ad6d2cb7c19f9e1df6a8803f51f687ddc2c46f121a2b96c`.

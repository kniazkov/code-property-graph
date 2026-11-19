# Virtual-base lifetime outcomes in unexpected handlers (#96)

## Русский

Сводки исходов различают полный объект и базовый подобъект. Полный объект сначала
создаёт уникальные виртуальные базы в глубинном порядке слева направо, затем
невиртуальные базы, поля и тело конструктора. Базовый подобъект пропускает свои
виртуальные базы: ими владеет наиболее производный объект. Разрушение выполняется
в обратном порядке. Общая база ромба учитывается один раз, включая typedef-имена
одного типа. Виртуальный и невиртуальный подобъекты одного типа остаются разными.

Поле-объект и каждый элемент массива являются полными объектами со своими
виртуальными базами. При сбое создания очищается только завершённый префикс.
Исключение из деструктора при раскрутке ведёт к завершению; общий виртуальный
деструктор не вызывается повторно через каждую ветвь ромба.

Спецификация промежуточного конструктора/деструктора проверяется только для
реально выполняемого им действия. Виртуальная база полного производного объекта
создаётся до вызова конструктора промежуточной базы; её исключение не проходит
через спецификацию промежуточного конструктора. Спецификация наиболее производной
специальной функции по-прежнему охватывает её виртуальные базы. Нарушения
спецификаций сохраняют неизвестность.

Отдельный CFG виртуального конструктора/деструктора без контекста вызова остаётся
неполным. При композиции lifetime анализируется только полное поддержанное
написанное тело, а скрытые операции с базами и полями добавляются отдельно в
известном контексте. Циклы, неизвестные вызовы, function-try-block и неполное тело
не становятся доказательством исхода.

Ограничения: создание по умолчанию без записанных инициализаторов, массивы 1–64,
восемь уровней классов и активных функций, бюджет 2000 посещений. Union, ссылки,
неизвестная/циклическая иерархия, копирование, временные объекты, параметры по
умолчанию и списки инициализаторов конструкторов не уточняются. Точные эффекты
памяти и копирования исключений не добавлены; #96 остаётся открытой.

Основание: сохранённый N1638 (11 апреля 2004), §12.6.2/5–6 и §12.4/6.
Повторно просмотрены порядок виртуальных баз, ответственность наиболее
производного класса и обратная очистка. Это не новая сверка оригинальной
печатной редакции ISO/IEC 14882:2003.

## English

Handler lifetime summaries distinguish complete objects from base subobjects.
Complete objects own unique virtual bases in depth-first, left-to-right order;
base invocations omit them. Nonvirtual bases, fields and the constructor body
follow. Destruction reverses construction. Shared diamond bases are not repeated,
including typedef aliases, while virtual and nonvirtual subobjects of the same
type remain distinct. Fields and array elements own independent virtual bases.

Failed construction cleans completed subobjects only. A base special member's
exception specification governs its base-context operations, not virtual-base
operations performed by the most-derived object. The most-derived specification
still covers those operations. Violations retain conservative alternatives.

Standalone virtual special-member CFGs remain incomplete because they lack an
invocation context. The contextual summary analyzes only a supported, complete
written body while composing hidden operations separately. It does not publish
unconditional virtual-base CFG edges or bypass unsupported-body guards. Existing
depth/visit bounds and fallback apply. Copies, temporaries, constructor initializer
lists, default arguments and exact memory effects remain outside this contract.
No AST/API/JSON schema change is introduced.

The retained April 2004 N1638 draft, sections 12.6.2/5–6 and 12.4/6, was reviewed;
this is not fresh verification of the original published 2003 edition.

## Verification

Base: `a92add2` (merged PR #178). Java 21; native programs use GCC
`-std=c++03 -pedantic-errors`. New original/generated fixtures check diamond and
nested-virtual construction/destruction order, partial virtual construction,
constructor-body failure, separate virtual bases of fields/array elements, and
base-context constructor/destructor specifications. Counters and traces are
checked independently of the branch-union analysis. Full CI including Clang
remains required before merge.

Verified 2026-11-19: **176 unit tests, one GCC E2E test, 53 scenarios / 106
original/generated native executions**, zero failures. Strict Javadoc, portable
paths, paragraph-ledger validation and diff checks passed.

```sh
mvn -B -ntp -DargLine=-Xmx768m -Dtest=UnexpectedHandlerVirtualLifetimeTest,UnexpectedHandlerCompositeLifetimeTest,UnexpectedHandlerLifetimeTest,ExceptionMemberPointerTest,ExceptionPointerQualificationTest,ExceptionBoundaryTest,ExceptionCleanupFlowTest,ArrayCleanupFlowTest,StatementExceptionExportTest,UnexpectedHandlerMemoryTest,UnexpectedHandlerOutcomesTest,UnexpectedHandlerStateTest,UnexpectedHandlerFlowTest,ObjectMemoryDataFlowTest,ObjectMemoryExportTest,JsonExportTest -Dit.test=ExceptionBoundaryIT#gccOriginalAndGenerated verify
```

Tested JAR SHA-256: `57893d911ae49b7206933fff3c097715be56c5afa695d8517b4445970f8d8670`.

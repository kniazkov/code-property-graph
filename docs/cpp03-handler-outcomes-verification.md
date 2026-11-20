# Outcomes of known unexpected handlers (#96)

## Русский

Продолжение PR #172. После анализа установок обработчика отдельный проход
строит сводку исходов тела каждой известной цели `UNEXPECTED_CALL`. Если все цели
поддержаны и их поведение не содержит неизвестности, из CFG удаляются заведомо
лишние варианты:

- разрешённое исключение — `EXCEPTION_REPLACEMENT` соответствующего типа списка;
- запрещённое исключение — `BAD_EXCEPTION_REPLACEMENT`, если список содержит
  стандартный `std::bad_exception`, иначе `TERMINATE_EXIT`;
- явный стандартный `std::terminate()` — только завершение;
- несколько ветвей или возможных обработчиков — объединение их исходов.

После удаления рёбер очищаются недостижимые вспомогательные операции. Индексы
оставшихся операций не перенумеровываются. Исходный AST и генерация не меняются.
API/JSON/DOT используют существующие виды рёбер/вершин и ссылки на кандидатов.
Копирование снимка и повторный анализ после правки handler сохраняют контракт.

Поддержаны последовательные блоки, if/else без угадывания значений условий,
скалярные/указательные выражения, прямые вызовы поддержанных функций, локальные
try/catch и повторный throw уже известного пойманного типа. Используется общий
с построением CFG алгоритм сопоставления типов: точное совпадение, простая
квалификация указателя и публичная однозначная база. Исправлено ошибочное
сопоставление указателя на функцию с void*: такое преобразование не является
допустимым преобразованием указателя на объект.

Основание исходов — рабочий проект N1638 §15.5.2/1–3. Это рабочий проект апреля
2004 года, а не повторная сверка оригинала ISO/IEC 14882:2003.

### Сохранённые границы

Неизвестная установленная цель, неизвестный вызов/тело, рекурсия, циклы/goto,
неподдержанная работа со временем жизни объектов не дают права удалять
альтернативы. Поддержанные локальные объекты и массивы описаны в
[контракте очистки](cpp03-handler-lifetime-verification.md), а смешанные
указатели — в [контракте типов](cpp03-member-exceptions.md). Для каждого события
анализ ограничен восемью активными функциями и 2000 посещениями функций/операторов.
Обычный return из unexpected_handler нарушает его контракт; такой путь оставляет
старую консервативную модель и не используется как корректный native oracle.
Повторный throw исходного неизвестного исключения также остаётся неизвестным.

Сводка определяет возможные исходы, не значения записей handler. Эффекты памяти
остаются консервативными, включая глобальные записи; успешный анализ исходов не
объявляет обработчик чистым. Исключения классов с потенциальными copy/destructor
эффектами, точные эффекты памяти, расширенные правила преобразований и исходная
нормативная сверка остаются в #96. Полное соответствие C++03 не заявляется.

## English

A post-provenance pass summarizes every known unexpected-handler body and removes
only alternatives contradicted by supported summaries. Allowed exceptions select
ordinary replacement events; rejected types select standard bad_exception
fallback or termination; direct std::terminate is terminal. Branches and candidate
handlers contribute a union of outcomes. Dead auxiliary edges are removed without
renumbering retained vertices or changing source syntax.

Supported summaries include blocks, branches, scalar/pointer expressions, direct
helper calls and local catch/rethrow. Unknown or unsupported behavior preserves
all prior alternatives. Selected automatic-object cleanup is covered by the
[lifetime contract](cpp03-handler-lifetime-verification.md); mixed pointer matching
is described in the [type contract](cpp03-member-exceptions.md). Returning handlers are not positive native
oracles. Existing opaque memory effects remain; this is not exact handler-body
execution or whole-program exception analysis. Issue #96 stays open.

## Test scope

Unit tests assert outgoing event kinds, bad_exception versus ordinary replacement,
termination/branch unions, helper calls, local catch/rethrow, unknown/recursive/
returning handlers, pointer qualification, numeric non-conversion, public/private/
ambiguous bases, function versus object pointers, conservative lifetime/nested
pointer fallback, memory effects, multiple targets, JSON/copy and reanalysis edits.

The native corpus contains 20 single-file scenarios plus the existing two-unit
scenario. Each is compiled and executed as original and generated strict C++03:
42 executions with GCC in the cloud. Clang and the complete suite are CI gates.
Logs remain under `target/exception-boundary-oracle` in CI artifacts.


### Executed on 2026-11-18

```sh
mvn -B -ntp -DargLine=-Xmx768m \
 -Dtest=UnexpectedHandlerOutcomesTest,UnexpectedHandlerStateTest,UnexpectedHandlerFlowTest,ExceptionBoundaryTest,ExceptionCleanupFlowTest,ArrayCleanupFlowTest,StatementExceptionExportTest,ObjectMemoryDataFlowTest,ObjectMemoryExportTest,JsonExportTest \
 '-Dit.test=ExceptionBoundaryIT#gccOriginalAndGenerated' verify
```

Result: 108 unit tests and one GCC E2E (21 scenarios, 42 original/generated
executions), zero failures/errors/skips; BUILD SUCCESS in 54.062 s.
Strict Javadoc (`-Werror -Xdoclint:all,-missing`), paragraph-ledger validation,
portable paths and diff checks passed. Full CI including Clang remains required.
Base revision: `fb02dab85e9df1afad592a1db29fee21330fd9cd`.
Fresh tested JAR SHA-256:
`d33b05ec3bb6caeedf72769345321492ac5515b004a7d2ea3092519b8f94c97a`.

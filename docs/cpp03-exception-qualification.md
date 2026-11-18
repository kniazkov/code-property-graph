# Exception pointer qualification (#96)

## Русский

Общий matcher catch/specification теперь проверяет cv-квалификацию на каждом
уровне указателя. Удалять const/volatile нельзя. Добавление квалификатора глубже
первого уровня допустимо, только если все промежуточные уровни назначения const.
Поэтому int** допускает const int*const*, но не const int**.

Преобразование в void* проверяет квалификацию самого объекта, на который указывает
исходный указатель, а не произвольный volatile внутри его типа. Преобразования
к базовому классу и void* не распространяются рекурсивно на несколько уровней.
Указатели на функции по-прежнему не преобразуются в void*.

Та же проверка используется в исходах unexpected_handler: снято прежнее общее
ограничение для вложенных обычных указателей, включая локальные catch/rethrow.
Указатели на члены, время жизни классов и точные эффекты памяти остаются вне этого
расширения. #96 остаётся открытой. Полное соответствие стандарту не заявляется.

## English

Shared catch/specification matching now implements multilevel cv qualification:
no qualifier removal, and deep additions require const at every intervening
pointer level in the destination. Object-to-void conversion examines immediate
pointee qualifiers only; base/void conversions do not recurse through pointers.
Known-handler outcome narrowing can now use this matching inside local catches
and at the violated boundary. Member pointers and exact lifetime/memory effects
remain outside this extension.

Tests cover two/three levels, qualifier removal, deep volatile versus immediate
volatile, nonrecursive void conversion, API catch/specification agreement and
original/generated native execution. Earlier handler and memory tests remain in
the focused regression suite. Full CI including Clang is required before merge.

Verification 2026-11-18: 112 unit tests and GCC E2E with 23 original/generated
scenarios (46 executions) passed in a standalone sequential run. Strict Javadoc,
portable paths and diff checks passed. Base: `4ec76af3ed65988c9940822cac4e140875a08b73`.
JAR SHA-256: `3e83bf043d7096b7f5f5ad1b7bd0419cdf668329e3ce7f3ed2efdeb3791a6fdf`.
Command: use the focused verify command in cpp03-handler-outcomes-verification.md
with ExceptionPointerQualificationTest added to -Dtest. Full CI remains required.

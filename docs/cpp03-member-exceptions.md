# Member pointers in exception matching (#96)

## Русский

Смешанная квалификация обычных указателей и указателей на члены теперь
разбирается послойно. На каждом уровне сохраняются вид указателя и класс-владелец;
глубокие cv-добавления требуют const на всех промежуточных уровнях назначения.
Запятые внутри типов функций не принимаются за разделитель владельца и типа члена.
Алгоритм общий для catch, спецификаций исключений и исходов unexpected_handler.

Важная граница профиля: сохранённый N1638 §15.3/3 допускает pointer/qualification
conversions для обработчика вида T*. Сам указатель на член не является T*.
Для него сохраняется точное совпадение (с игнорированием верхнего cv), без
автоматического преобразования владельца base/derived или добавления cv типа члена.
§4.4/5–7 описывает квалификацию смешанных многоуровневых типов. Это проверка
сохранённого проекта 2004 года, не новая сверка оригинальной печатной редакции.
Современные DR и расширения хостового компилятора не вводятся молча в профиль.

Нативные тесты используют точное совпадение и смешанный внешний обычный указатель.
Отрицательные преобразования прямых указателей на члены проверяются на уровне
модели, не объявляются ill-formed программами и не используются как native oracle.
Точные эффекты памяти и времени жизни обработчиков остаются в открытой #96.

## English

Qualification matching now decomposes mixed pointer/member-pointer types while
preserving pointer kind and owner identity at every level. Deep additions require
const at all intervening destination levels; function parameter commas remain
inside their type. The shared matcher serves CFG and known-handler outcomes.

The retained N1638 §15.3/3 profile applies pointer conversions to a T* catch type,
not directly to a pointer-to-member catch type. Direct member pointers therefore
require identity; owner base/derived conversions and direct pointee cv additions
are not silently imported from later defect resolutions. Mixed qualification
follows §4.4/5–7. Original-edition verification and exact lifetime/memory effects
remain open. Full CI including Clang is required before merge.

Verification 2026-11-18: 117 unit tests and GCC E2E with 25 scenarios
(50 original/generated executions) passed. Strict Javadoc, portable paths and
diff checks passed. Base: `1125852f9973f22fbd262f858f367f143efaad8b`.
JAR SHA-256: `cb020884dc0c3c5219831fc839ed443e192fb0bdcf7b0cca7a6ad2593ad6c860`.
Use the focused verify command from cpp03-handler-outcomes-verification.md with
ExceptionPointerQualificationTest and ExceptionMemberPointerTest added to -Dtest.
Full CI including Clang remains required.

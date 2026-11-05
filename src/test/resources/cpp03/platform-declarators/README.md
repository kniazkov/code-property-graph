# Platform profiles and recursive declarators / Профили и вложенные деклараторы

The source is strict C++03 and uses the default LP64-like profile. The corpus
compiles and executes both original and generated sources with GCC and Clang.
Its return value is `sizeof(long) + sizeof(int[3])`, hence twenty for the
supplied default profile. This native oracle does not claim that an arbitrary
custom target profile matches the host compiler ABI.

The API and packaged CLI acceptance tests additionally load `long32.json`,
which changes `long` to 32 bits without changing the source. They verify the
profile-dependent result, stable distinct TU identities, JSON configuration,
and the difference between an array of pointers, a pointer to an array, and a
pointer to a function. The fixture also includes typedef spelling/identity,
cv-qualified pointers, a reference, a member pointer, abstract parameters and
multiple local declarators.

Исходник соответствует строгому C++03. Обязательный корпус собирает и запускает
исходный и сгенерированный варианты GCC и Clang с профилем по умолчанию.
Возвращаемое значение равно `sizeof(long) + sizeof(int[3])`, то есть 20.
Произвольный пользовательский профиль не объявляется совместимым с ABI хоста.

Дополнительная приёмка API и упакованного CLI применяет `long32.json` к тому же
исходнику и проверяет зависимый от профиля результат, идентичность вариантов,
конфигурацию JSON и уровни вложенности деклараторов. Написание typedef
сохраняется отдельно от идентичности типа.

The `signed-min` companion compares an `int` and a `long` overload. Both original
and folded generated programs must return 1 under strict GCC and Clang: folding
`-1073741824 * 2` must not turn its expression type into `long` when rendered.
This is a native source-preservation oracle, not a claim that overload resolution
is implemented in this milestone.

Подсценарий `signed-min` проверяет GCC/Clang, что свёртка минимального `int`
сохраняет тип выражения и выбор перегрузки при генерации. Ожидаемый код выхода
обоих вариантов — 1; это проверка исходников, не завершение разрешения перегрузок.

The current pre-#24/#30 name resolver may label the two same-name overloads as
an ODR conflict; this companion does not use that incomplete binding result as
its oracle. The parser and exported source remain available, and the strict
native compiler establishes the result. Полноценная диагностика перегрузок
остаётся отдельной задачей связывания и разрешения перегрузок.

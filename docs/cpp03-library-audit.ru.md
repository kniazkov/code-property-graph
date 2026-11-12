# Приёмочный аудит библиотеки C++03: главы 17–27

[English version](cpp03-library-audit.en.md)

## Вывод и границы

Это аудит доказательств для задачи #48, а не заявление о полном соответствии ISO
C++03. Исходная проверенная версия: `9420887` (`cpp03-base48`). Позднее полученный
оригинал 2003 года исправляет первоначальный подсчёт по каталогу: таблица 11
требует **33 C++-заголовка**, включая устаревший `<strstream>`, а таблица 12
добавляет 18 c-заголовков, всего **51 обязательный заголовок**. Каталог распознаёт
только 50 из них и 18 старых C-написаний (68 из 69 написаний). `<strstream>`
отсутствует. Даже полный список не доказывал бы все перегрузки, макросы и поведение.

Каталог существенно шире поведенческой модели. Все его вызываемые сущности
являются объявлениями без тел. Некоторые операции vector/контейнеров/строк/
итераторов/алгоритмов получают общую семантику графа; остальные разрешённые
библиотечные вызовы сохраняют консервативные эффекты. Разобранное объявление,
разрешённый вызов, полный CFG/DFG и правильный результат нативной программы
доказывают разные вещи. В частности, `FunctionFlow.complete()` не означает
полноту семантики STL.

**Известные пробелы:** обязательные целевые макросы не предоставляются; у многих
объявленных перегрузок нет независимого теста вызова в CPG; большинство семейств
не имеет поведенческих сводок; операции файловых потоков и streambuf не имеют
отдельной приёмки CPG; чтение источника не устраняет перечисленные ниже пробелы
реализации и регрессионных тестов. Неизвестное ABI и консервативные эффекты являются защитными границами,
но не подтверждают реализацию отсутствующего средства. Пробелы запрещают
заявление о полной поддержке библиотеки, но не завершение самого аудита.

## Редакция и источники

Нормативная цель: [ISO/IEC 14882:2003, второе издание](https://www.iso.org/standard/38110.html).
Пользователь затем предоставил подлинный PDF редакции 2003 года. Обложка указывает
второе издание от 2003-10-15 и принятие ANSI от 2003-12-29. В PDF 786 страниц,
включая основной текст/указатели с печатной нумерацией до 757. Независимо проверена
SHA-256: `6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.
Прежний вывод о недоступном источнике и нуле просмотренных параграфов устарел.
Публикуются только самостоятельно сформулированные пересказы, координаты и
доказательства; PDF и извлечённый текст не добавляются в репозиторий.

[Реестр параграфов](cpp03-paragraphs/clauses-17-22.jsonl) содержит проверенные по
источнику записи для всех **1 382 текущих координат инвентаря глав 17–22**:
1 067 нумерованных параграфов (включая четыре с буквенным суффиксом), 158
ненумерованных определений/блоков объявлений, 53 таблицы, 103 сноски и одну
аномалию отдельной метки номера. Двенадцать ложных грамматических кандидатов
отклонены как обычный текст. Сноски имеют глобальные идентификаторы и явные
ссылки на места вызова, а не приписываются последнему заголовку страницы.

Итоги доказательств реализации: **1 226 пробелов, 29 частичных и 127 неприменимых
пунктов**. Полностью подтверждённых строк реализации нет. Каждая запись имеет
пересказ правила, предложенный пример либо обоснование неисполнимого случая,
ожидаемое поведение, состояния этапов и ограниченные доказательства либо пробел.
Запись предложенного примера не означает его запуск. Ссылки на тесты семейства
доказывают только указанную в пробеле часть объявлений/разрешения, а не каждый
связанный параграф. Неприменимость относится к пояснениям и организации текста,
включая сноски и отдельный номер, а не к отсутствующей семантике.

Проверенная по источнику строка с пробелом означает завершённое чтение правила,
а не его реализацию. Большая таблица или параграф может содержать несколько
обязанностей, поэтому покрытие координат не является полным знаменателем
нормативных правил. Инвентарь, чтение источника, точные тесты и реализация
учитываются раздельно. Процент полного соответствия из этих чисел не вычисляется.
Другие реестры координируются отдельно в [языковом аудите](cpp03-language-audit.ru.md).

[WG21 N1638 от 11 апреля 2004 года](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2004/n1638.pdf)
является более поздним рабочим проектом. [Справка репозитория](standards/cpp/cpp03/README.ru.md)
ссылается на отслеживаемый PDF, **не материализованный в рабочей копии**
(`git ls-files -v` отмечает skip-worktree). Это граница восстановления рабочей
копии, не отсутствие файла в репозитории. Записанная контрольная сумма здесь
не подтверждена локальными байтами.
[N1578](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2004/n1578.html) и
[N1639](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2004/n1639.html)
описывают изменения после редакции 2003 года. В частности, N1639 рассматривает
LWG 44 (сравнение символа/EOF), 389 (исправление объявления) и 395 (превращение
примечания в нормативный текст). Нельзя автоматически считать их требованиями
исходного издания 2003 года. Полной карты «объявление — исправление дефекта» нет.
Подтверждённое сознательное отличие: печатные §23.2.2.4/4, /7 и /12 инвалидируют
итераторы/ссылки перемещённых элементов `list::splice`, а реализованная модель
сохраняет их и переносит владельца согласно исправленному контракту равных
аллокаторов [LWG 250](https://cplusplus.github.io/LWG/issue250).
Проверенный исправленный профиль сохраняется; это не буквальное поведение
напечатанной редакции 2003 года.
Аналогично, стирание vector в
[DynamicArraySemantics](../src/main/java/com/kniazkov/cpg/analysis/DynamicArraySemantics.java)
инвалидирует начиная с удаляемой позиции, тогда как печатный §23.2.4.3/3 говорит
о позициях после неё. Это исправление [LWG 414](https://cplusplus.github.io/LWG/issue414),
а не буквальная исходная граница.

Реестр сохраняет противоречия оригинала, а не исправляет их молча. Например,
§21.3.5.6/12 действительно печатает `begin()-i1` как длину префикса (страница 402),
а некоторые строковые границы одновременно имеют Requires и противоположный
Throws. Это вопросы источника/профиля, не выдуманная обязательная диагностика
компилятора. Дополнительные несогласованности сигнатур locale отмечены в
соответствующих записях реестра.

Номера глав ниже группируют семейства, включая средства C, используемые в
нескольких главах. Это не проверенный список всех нормативных параграфов.
Современные GCC/Clang с `-std=c++03 -pedantic-errors` полезны как независимая
проверка реализации, но включают исправления дефектов и не заменяют исходное
издание. TR1, средства только C99 и добавления C++11 не входят в требования
приёмки. Доказательства языковых правил приведены в отдельном языковом аудите;
этот файл не выдаёт поддержку языка за покрытие библиотеки.

## Обозначения доказательств

Все ссылки на Java-тесты ведут в `src/test/java/com/kniazkov/cpg/`.
Имя метода после обозначения указывает конкретный тест, а не только включение
заголовка.

| Обозначение | Источник и предел доказательства |
|---|---|
| D | [StandardLibraryCatalogueTest](../src/test/java/com/kniazkov/cpg/StandardLibraryCatalogueTest.java): `catalogueRecognizesExactlyCpp03HeadersAndCompatibilityNames`, `everyFragmentParsesWithoutHostHeadersOnBothSizeTypePolicies`, `parserNameIndexExactlyMatchesDeclaredNamespaceTypes`. Разбор 68 фрагментов с зависимостями; тела отсутствуют. Не доказано разрешение всех выражений. |
| O | [RemainingLibraryOverloadsTest](../src/test/java/com/kniazkov/cpg/RemainingLibraryOverloadsTest.java) и [явный список количества перегрузок](../src/test/resources/library-catalogue/cpp03-remaining-overloads.tsv). Проверяет выбранные группы владелец/имя, не каждую сигнатуру и подстановку. |
| R | [RemainingLibraryResolutionTest](../src/test/java/com/kniazkov/cpg/RemainingLibraryResolutionTest.java) и [входы CPG](../src/test/resources/library-catalogue/resolved-remaining/). Выбранные объявления, параметры и операторы; тела не создаются. |
| C | [CCompatibilityFamiliesTest](../src/test/java/com/kniazkov/cpg/CCompatibilityFamiliesTest.java). Оба пространства имён, точные математические перегрузки, выбранные вызовы, непрозрачные типы, отсутствующие макросы и исключение поздних API. |
| F | [LibraryCatalogueFamiliesTest](../src/test/java/com/kniazkov/cpg/LibraryCatalogueFamiliesTest.java). Выбранные семейства объявлений, непрозрачные маски; stdio.h не создаёт вымышленный std::printf. |
| S | [StreamLibraryResolutionTest](../src/test/java/com/kniazkov/cpg/StreamLibraryResolutionTest.java). Наследуемые члены узких/широких строковых потоков, итераторы ввода-вывода, манипуляторы, операторы и границы вывода параметров. |
| U | [LibraryUnknownEffectsTest](../src/test/java/com/kniazkov/cpg/LibraryUnknownEffectsTest.java). Возможные записи через получатель/ссылки/указатели/доступную и глобальную память; псевдонимы потоков и неподдержанное время жизни временных объектов. |
| L | [LazyLibraryGraphTest](../src/test/java/com/kniazkov/cpg/LazyLibraryGraphTest.java). Замыкание востребованных объявлений, происхождение, владение, сброс после правки/смены профиля, область include внутри единицы трансляции. |
| V | [VectorResolutionTest](../src/test/java/com/kniazkov/cpg/VectorResolutionTest.java), [VectorIteratorResolutionTest](../src/test/java/com/kniazkov/cpg/VectorIteratorResolutionTest.java), [VectorAccessNormalizationTest](../src/test/java/com/kniazkov/cpg/VectorAccessNormalizationTest.java). Конкретные vector-вызовы, итераторы, ёмкость/инвалидация; границы proxy и нетривиальных типов. |
| K | [ContainerIteratorResolutionTest](../src/test/java/com/kniazkov/cpg/ContainerIteratorResolutionTest.java), [ContainerNormalizationTest](../src/test/java/com/kniazkov/cpg/ContainerNormalizationTest.java), [ContainerFamilyFlowTest](../src/test/java/com/kniazkov/cpg/ContainerFamilyFlowTest.java). Операции deque/list/ассоциативных контейнеров/адаптеров и эффекты памяти. |
| Q | [StringAlgorithmResolutionTest](../src/test/java/com/kniazkov/cpg/StringAlgorithmResolutionTest.java), [SequenceNormalizationTest](../src/test/java/com/kniazkov/cpg/SequenceNormalizationTest.java), [SequenceFlowTest](../src/test/java/com/kniazkov/cpg/SequenceFlowTest.java), [SequenceModelTest](../src/test/java/com/kniazkov/cpg/SequenceModelTest.java). Выбранные строки/алгоритмы диапазонов, эффекты функций и вывода, копирование/сброс графа. |
| N | [Скрипт нативных сигнатур](../scripts/probe-cpp03-library-catalogue.sh): [контейнеры](../src/test/resources/library-catalogue/containers.cpp), [остальные C++-сигнатуры](../src/test/resources/library-catalogue/remaining-cpp-signatures.cpp), [C-сигнатуры](../src/test/resources/library-catalogue/c-compatibility-signatures.cpp), [старые написания C](../src/test/resources/library-catalogue/c-legacy-signatures.cpp), входы R. Только синтаксис, кроме двух безопасных программ; проверка STL хоста, не разрешения CPG. |
| B | Только нативные [безопасная C-программа](../src/test/resources/library-catalogue/c-benign-runtime.cpp) и [форматирование потока](../src/test/resources/library-catalogue/c-stream-signatures.cpp). Реальные проверки значений. assert/va_* здесь берутся из заголовков хоста, а не из CPG. |
| E | [Cpp03CorpusIT](../src/test/java/com/kniazkov/cpg/e2e/Cpp03CorpusIT.java): сценарии `lazy-library`, `vector-semantics`, `vector-access`, `container-families`, `strings-algorithms`, `remaining-library`. Исходные/сгенерированные/свёрнутые программы, граф/JSON/DOT/SVG, строгие нативные GCC/Clang проверки этих входов. Не отдельный сценарий для каждого заголовка. |

Реализация объявлений: [StandardLibraryCatalogue](../src/main/java/com/kniazkov/cpg/analysis/StandardLibraryCatalogue.java),
[StandardLibraryC](../src/main/java/com/kniazkov/cpg/analysis/StandardLibraryC.java),
[StandardLibraryStreams](../src/main/java/com/kniazkov/cpg/analysis/StandardLibraryStreams.java).
Их точные объявления остаются источником истины; таблицы содержат **проверенные
примеры**, а не замену полного synopsis. Индексы имён типов/шаблонов проверяются
отдельно от объявлений и разрешения.

Эффект **непрозрачный** означает возможные обращения, сохранённые
[LibraryCallEffects](../src/main/java/com/kniazkov/cpg/analysis/LibraryCallEffects.java),
а не чистоту, отсутствие исключений или известную реализацию.
**Общая модель** означает поддержанный случай в
[ContainerSemantics](../src/main/java/com/kniazkov/cpg/analysis/ContainerSemantics.java) или
[SequenceSemantics](../src/main/java/com/kniazkov/cpg/analysis/SequenceSemantics.java)
с указанным V/K/Q-доказательством. Остальные случаи остаются обычным C++.
Генератор сохраняет вызовы и include, а не создаёт реализацию STL; E проверяет
только выбранные программы.

## 33 обязательных C++-заголовка (32 в каталоге)

Для каждой имеющейся в каталоге строки есть разбор D; для отсутствующего strstream его нет. «Только D» прямо
означает отсутствие отдельного теста разрешения/поведения данного интерфейса.
`C`/`T` сокращают тип символа/значения и не обещают произвольные пользовательские
типы.

| Заголовок; глава | Проверенные объявления / сигнатуры | CPG, эффекты и генерация | Явная граница |
|---|---|---|---|
| `algorithm`; 25 | C++03 `void rotate`, `void fill_n`, `void generate_n`; copy/find_if/унарный transform/sort/remove; общий swap(T&,T&) объявлен здесь. | D.`cxx03AlgorithmReturnTypesDoNotBorrowCxx11Signatures`; Q.`iteratorArgumentsAndUserFunctorsSurviveOwnedInstantiation`, `algorithmsRetainRangesOutputsCallbacksAndAccumulator`, `removeAndSortMutateRangeWithoutSizeChange`; U.`opaqueReferenceCallMayWriteArgumentsAndKeepIncomingDefinitions`; E `strings-algorithms`. Общая модель выбранных диапазонов/функций, прочее непрозрачно. | Нет проверки каждой перегрузки поиска/partition/merge/set/heap/permutation. Неподходящие категории и const-выходы остаются исходными (Q), не полностью диагностируются. Нет доказательства сложности и всех предусловий. |
| `bitset`; 23 | 3 группы конструкторов; по 2 set/reset/flip; proxy/const operator[]; явный to_string<C,Traits,Alloc>(); to_ulong, count/проверки, битовые операторы. | O; R.`bitsetOverloadsAndExplicitStringTemplate` (14 вызовов), `bitsetOperatorTargets` (13); N `bits`. Непрозрачно. | Нет модели битовых значений/переполнения/исключений позиции и отдельного нативного сценария генерации. Количества не доказывают все аргументы шаблона. |
| `complex`; 26 | Основной шаблон и float/double/long-double; pow(complex,int), complex/scalar, complex/complex, scalar/complex; арифметика, real/imag, трансцендентные функции. | O; R.`complexScalarAndComplexPowerOverloads` (16 вызовов), `complexOperatorTargets` (28); N `complex_specializations`. Непрозрачно. | Нет модели точности/областей/ветвлений функций и полного доказательства преобразований специализаций/потокового исполнения. |
| `deque`; 23 | push_front/back, pop_front/back, index/at/front/back, конкретные итераторы произвольного доступа; объявления конструкторов/модификаторов/сравнений. | K.`sequencesSelectConcreteOperationsAndRespectIteratorCategories`, `dequePushPreservesReferencesButNotIteratorProofs`; E `container-families`. Общая модель выбранных обращений/изменений. | Не все конструкторы/диапазоны/аллокаторы/гарантии исключений. Инвалидация ссылок отличается от инвалидации итераторов. |
| `exception`; 18 | exception/bad_exception, копирование/присваивание/what, типы обработчиков и set_terminate/set_unexpected/terminate/unexpected/uncaught_exception. | O; R.`exceptionHandlersAndTypeInfo` (8 вызовов суммарно exception/new/typeinfo); N указатели на функции и `exceptions`. Непрозрачно. | Установка обработчиков/завершение не исполняются; нет полной модели исходов спецификаций исключений. |
| `fstream`; 27 | basic_filebuf, basic_ifstream/ofstream/fstream, узкие/широкие псевдонимы; open/close/is_open/rdbuf, защищённые буферные методы. | Для этого интерфейса только D. B включает заголовок, но не выполняет файловые операции. Непрозрачно. | Нет отдельных CPG-тестов open/seek/close и исходного/сгенерированного файлового сценария. Включение заголовка не доказывает операции. |
| `functional`; 20 | Арифметические/сравнивающие/логические функторы, отрицатели, bind1st/bind2nd, ptr_fun (2), mem_fun (4), mem_fun_ref (4), const/nonconst адаптеры. | O; R.`functionalAdapters` (14 вызовов); N `functional`; Q сохраняет возможные эффекты функций. Вне доказанного общего применения непрозрачно. | Нет всеобщей чистоты и доказательства каждой подстановки адаптера; пользовательское состояние требует консервативных эффектов. |
| `iomanip`; 27 | setiosflags/resetiosflags, setbase, setfill<C>, setprecision, setw; типы результатов без заданного представления. | S.`parameterizedManipulatorsResolveWithoutRepresentationAssumptions`, `setfillRetainsItsCharacterTypeAndDoesNotInventExtraction`; B форматирование; E `remaining-library`. Непрозрачно. | Неподходящий тип символа/применение setfill не нормализуются; представление proxy не является знанием ABI. Нет общего симулятора форматирования. |
| `ios`; 27 | Флаги/состояние/locale/обратные вызовы/хранилище ios_base, basic_ios; clear/rdstate/exceptions/fill/width/precision/flags. | S.`narrowAndWideStringStreamAliasesExposeInheritedStateAndPosition`; U.`streamStateMutatorIsAnOpaqueReceiverWrite`; F.`bitmaskTypesDoNotPretendToBeIntegralAbiTypes`; E `remaining-library`. Непрозрачно. | Не покрыты полностью callback/xalloc/iword/pword/Init/failure и числовые флаги; нативно проверяются выбранные состояния. |
| `iosfwd`; 27 | Предварительные объявления потоков, char/wchar-псевдонимы, fpos<State>, непрозрачные смещения; строковые потоки сохраняют аллокатор. | D.`stringStreamsRetainTheirThirdAllocatorParameter`; S.`defaultsFromForwardDeclarationsSubstituteTheirOwnParameters`; B объявления типов. Зависимые вызовы непрозрачны. | Видимость предварительных объявлений не доказывает всю арифметику/состояние fpos и все семейства потоков. |
| `iostream`; 27 | 8 стандартных узких/широких объектов; выбранные cout << value << endl и cin >> value. | [LibraryConsoleTest](../src/test/java/com/kniazkov/cpg/LibraryConsoleTest.java).`catalogueBoundCoutAndEndlNormalize` / `userNamespaceWithSameSpellingDoesNotNormalize`; U.`opaqueLibraryOperatorMayChangeItsReceiverReferenceArgumentAndAccessibleData`. Узкая доказанная форма консоли имеет общую модель, прочее непрозрачно. | Нет полного поведения инициализации/tie/sync всех 8 объектов. Пользовательским одноимённым объектам факты консоли не приписываются. |
| `istream`; 27 | basic_istream, sentry, форматированный/неформатированный ввод, позиция, basic_iostream; символьные/указательные/манипуляторные перегрузки. | S наследуемый ввод/итераторы; U.`chainedStringStreamExtractionRetainsBothOutputAndReceiverEffects`; E `remaining-library`: значения, EOF/fail, clear и повторное чтение. Непрозрачно. | Не все get/getline/read/putback/ignore/sentry и исключения; происхождение изменений LWG после 2003 требует проверки. |
| `iterator`; 24 | Traits/tags, специализации указателей, reverse/insert/I/O-адаптеры; advance(I&,D), distance(I,I), конкретные итераторы контейнеров. | Q расстояние указателей/reverse и сохранённые адаптеры; S.`inputAndOutputIteratorFamiliesResolveTheirOperators`; U сохранённые/копированные/переприсвоенные потоковые итераторы; E строки/потоки. Общая модель обычных диапазонов, I/O непрозрачен. | Неподходящие категории/чужие итераторы не получают доказанных операций. Нет универсальной проверки законов итераторов; postfix-результат istreambuf непрозрачен. |
| `limits`; 18 | numeric_limits<T> и 14 скалярных специализаций, 8 функций значений, статические свойства, enum округления/денормализации. | O.`numericLimitsPreserveUnknownTargetConstantsAndAllScalarSpecializations`; R.`numericLimitsSpecializationTargets` (5 вызовов); N `scalars`. Функции непрозрачны, константы без инициализаторов. | Целевые min/max/NaN/epsilon/radix не задаются и не сворачиваются; ABI/формат не угадывается, вычисление всех членов не доказано. |
| `list`; 23 | Двунаправленная итерация, insert/erase/front/back, 3 splice, объявления remove/unique/merge/sort/reverse. | K.`listSpliceTransfersStorageWithoutCopying`, `allAndRangeSpliceRetainTheirWrittenBounds`, `recordSplicePreservesStorageWithoutCopyOrDestruction`, условные/пустые/end-случаи; E `container-families`. Общая модель выбранных перемещений/изменений. | Не доказаны общие границы диапазонов/совместимость аллокаторов/merge/sort/исключения; арифметика итераторов неподдержана. |
| `locale`; 22 | locale/facet/id; use_facet<T>/has_facet<T>; ctype/codecvt, числовые/сравнивающие/временные/денежные/messages и byname-семейства. | O количества, включая num_get 9 и num_put 6; R.`localeFacetsAndCharacterOverloads` (23 вызова); F семейства; N `facets`, `numeric_time_money`. Непрозрачно. | Нет семантики изменения locale/каталогов/жизни facet/сравнения/состояния преобразования; нет отдельного отрицательного теста неверного facet. |
| `map`; 23 | map/multimap; operator[] только map; find/границы, insert возвращает pair для unique и iterator для multi, erase/наблюдатели/сравнения. | K.`associativeIteratorsPreserveConstKeysAndMappedReferences`, `mapLookupAndSubscriptHaveDifferentEffects`, `mapScalarPairInsertProducesValueResult`, DFG ссылок mapped; E `container-families`. Общая модель выбранного. | Не все компараторы/аллокаторы/hint/диапазоны/исключения; возможный end поиска не объявляется корректным элементом. |
| `memory`; 20 | allocator address const/nonconst, allocate/deallocate/construct/destroy; allocator<void>/rebind; auto_ptr get/release/reset/преобразования; временные буферы и uninitialized-алгоритмы. | O; R.`allocatorOwnershipAndUninitializedAlgorithms` (17 вызовов, constness параметров address); U.`allocatorReturnedPointerKeepsPossiblePointeeWrites`; N `utilities`. Непрозрачно. | Нет полной модели передачи владения/ошибки выделения/отката конструирования/raw-storage/uninitialized-жизни. Разрешение не доказывает безопасность памяти. |
| `new`; 18 | 12 глобальных new/new[]/delete/delete[]: обычные, nothrow, placement; bad_alloc/nothrow_t/new_handler. | D.`allocationOverloadsStayInTheGlobalNamespace`; O; R.`allocationOverloadTargets` выбирает все 12; N точные типы указателей. Непрозрачно. | Каталог не доказывает исполнение выделения, ошибки, placement-время жизни и гарантии исключений. |
| `numeric`; 26 | По 2 accumulate/inner_product/partial_sum/adjacent_difference; выбран accumulate(first,last,0L). | D; Q.`iteratorArgumentsAndUserFunctorsSurviveOwnedInstantiation`, `algorithmsRetainRangesOutputsCallbacksAndAccumulator`; E `strings-algorithms`. Общая модель выбранного накопления. | Нет независимой CPG-проверки всех binary-op перегрузок и остальных 3 семейств, общей проверки переполнения/порядка/сложности. |
| `ostream`; 27 | basic_ostream/sentry, вывод значений/символов/указателей, put/write/flush/seekp/tellp; endl/ends/flush. | S.`characterInsertionUsesSpecializedOperatorTemplates`, наследуемая позиция; U операторы; B точная сигнатура const-void-pointer; E `remaining-library`. Непрозрачно, кроме узкой нормализации консоли. | Нет всей семантики sentry/неформатированного вывода/ошибок/flush. Строковые потоки не превращаются в консольные PrintStatement. |
| `queue`; 23 | queue/priority_queue; front/back против const top, push/pop/size/empty; сравнения queue, без равенства priority_queue. | K.`adaptersAndCustomComparatorsRetainSelectedTargets`, `adapterOrdersAndPriorityMutabilityAreDistinct`, `adapterReferencesDifferFromCopiesAndPopWrites`; E `container-families`. Общая модель выбранного порядка/доступа. | Не все пользовательские контейнеры/компараторы, исключения и конструкторы; нет позднего adaptor swap. |
| `set`; 23 | set/multiset, неизменяемые ключи; insert возвращает pair/iterator; find/границы/equal_range/наблюдатели. | K ассоциативные итераторы, `customComparatorHasSelectedCallAndNoInventedComparisonCount`, `possibleEndResultsAreNotDefinitelyInvalidatedByClear`; E `container-families`. Общая модель выбранного. | Нет всеобщего доказательства порядка компаратора, всех equal_range/hint/диапазонов/аллокаторов/исключений. |
| `sstream`; 27 | basic_stringbuf и basic_i/o/stringstream с 3 параметрами; конструкторы/str/rdbuf, унаследованное состояние. | S узкие/широкие состояния/позиции; U ввод/материализация/псевдонимы; E `remaining-library`: значения/состояния исходного/сгенерированного/свёрнутого кода. Непрозрачно. | Время жизни отброшенного/немоделируемого возвращённого объекта явно неполно (U); не все буферные hooks/конструкторы/locale. |
| `stack`; 23 | stack<T,Container>, top const/nonconst, push/pop/size/empty, сравнения. | K разрешение/порядок/ссылки адаптеров; E `container-families`. Общая модель выбранного LIFO-доступа/изменения. | Нет доказательства всех контейнеров/конструкторов/сравнений/исключительных подстановок. |
| `stdexcept`; 19 | 9 классов исключений, строковые конструкторы, иерархия logic_error/runtime_error, what. | O; R.`standardExceptionHierarchy` (9 вызовов); N `exceptions`. Непрозрачно. | Нет отдельного сгенерированного сценария throw/catch/сообщение/жизнь для всех 9 и точного содержимого what. |
| `streambuf`; 27 | Публичные get/put/seek/locale методы basic_streambuf и защищённые виртуальные hooks. | Разбор интерфейса D; итераторы S зависят от типов streambuf, но не проверяют каждый hook. Непрозрачно. | Нет отдельной CPG-проверки публичных/защищённых целей, пользовательского переопределения, сгенерированного сценария буферизации/позиции. |
| `strstream`; таблица 11 главы 17 / D.7 | Обязательный устаревший заголовок с интерфейсами strstreambuf/istrstream/ostrstream/strstream. | **Отсутствует в StandardLibraryCatalogue**. Нынешний тест утверждает лишь 50 канонических имён и не доказывает состав исходной таблицы 11. | Отсутствует целое обязательное семейство объявлений; нет приёмки парсера/разрешения/эффектов/генерации. |
| `string`; 21 | char_traits-специализации, basic_string/string/wstring; counted/NUL/fill-конструкторы, append/assign/find/substr/доступ/итерация; более широкий набор объявлений. | Q.`stringAliasesDemandConcreteMembersAndIteratorTypes`, `explicitCharacterTraitsUseTheSelectedCharacterSpecialization`, NUL/подстроки/эффекты/память; E `strings-algorithms`. Общая модель выбранных строк. | Нет общей гарантии для своих traits/аллокаторов/специализаций, многих insert/erase/replace/search/сравнений и нетривиальных временных объектов. |
| `typeinfo`; 18 | type_info ==/!=/before/name, недоступное копирование, bad_cast/bad_typeid. | O; вход R exception/typeinfo проверяет before/name; N `exceptions` также ==/!=. Непрозрачно. | Не симулируются строки name, порядок/тождество/ошибки RTTI; не каждый оператор/запрет копирования проверяется отдельным CPG-тестом. |
| `utility`; 20 | Конструкторы/типы/поля pair, make_pair(T1,T2), 6 сравнений. | O; R.`utilityTemplates` (1 вызов), `pairComparisonTargets` (6); K поток unique-insert pair; N `utilities`. Непрозрачно, кроме отдельно доказанной структуры результата контейнера. | Подтверждён пробел: исходные §20.2/1 и §20.2.1 требуют четыре общих оператора std::rel_ops, отсутствующих в каталоге. Поведение pair/конструкторов покрыто частично; общий swap объявлен не здесь. |
| `valarray`; 26 | valarray/slice/gslice и 4 proxy-семейства; 10 index-групп, 6 присваиваний, apply по значению/const-ссылке, арифметика/сравнения/математика. | O; R.`valarrayApplyAndReductions` (10 вызовов), `valarrayOperatorTargets` (54), `valarrayMathOverloads` (20); N `arrays`, `array_math`. Непрозрачно. | Нет модели чисел/proxy-псевдонимов/размеров/границ/жизни/исключений или генерационного исполнения; expression templates не означают известный layout. |
| `vector`; 23 | Основной шаблон и bool, конкретные конструкторы/assign/push/доступ/ёмкость/итерация/модификаторы, bool reference proxy. | L замыкание; V диапазоны/ёмкость/инвалидация/const/proxy-идентичность; E `lazy-library`, `vector-semantics`, `vector-access`. Общая модель поддержанных default-allocator случаев. | V.`boolRemainsRaw`, `nontrivialAssignmentShiftsRemainRaw`, условная ёмкость/пользовательские специализации: нет ложных bool&, гарантий жизни сдвига, коэффициента роста или всех аллокаторов. |

## 18 C-заголовков и 18 устаревших написаний

Каждая строка отдельно покрывает оба указанных написания. D и C проверяют оба
пространства имён: `<c...>` предоставляет гарантированные std-имена, `<...h>` —
глобальные. Необязательные дополнительные импорты не обещаются.
**Непрозрачно** означает консервативные эффекты CPG; даже значения нативной
арифметики B не доказывают вычисление констант CPG. 180 выборов сигнатур N на
каждое написание описаны в исходниках и не означают 180 полных семантик.

| Заголовок / устаревшее написание; глава | Проверенные объявления / макросная граница | Доказательства и эффекты | Пробел / уточнение |
|---|---|---|---|
| `cassert` / `assert.h`; 19 | Пустой фрагмент: assert — макрос, не функция. | C.`macroOnlyHeadersDoNotInventObjectsOrFunctions`; N наличие макроса; B исполняет assert хоста. Встроенного CPG-эффекта макроса нет. | Нет раскрытия assert и поведения NDEBUG. Нативный успех не закрывает пробел. |
| `cctype` / `ctype.h`; 21/22 | 11 классификаторов int, int tolower(int)/toupper(int). | C.`scalarAndCharacterFamiliesResolveUnderBothSpellings`: isdigit/toupper; N сигнатуры; B значения стартовой C-locale. Непрозрачно. | Нет CPG-вычисления locale/значений и диагностики аргумента вне unsigned-char/EOF; поздний isblank исключён. |
| `cerrno` / `errno.h`; 19 | Пустой фрагмент, нет вымышленного объекта errno/числовых констант. | C.`macroOnlyHeadersDoNotInventObjectsOrFunctions`, `absentTargetMacroDefinitionsRemainUnresolved`; N наличие на хосте. | Нет определений/значений/состояния errno и EDOM/ERANGE; это не «поддержка с неизвестным значением». |
| `cfloat` / `float.h`; 18 | Пустой фрагмент, значения FLT/DBL/LDBL не угадываются. | C оставляет FLT_RADIX неразрешённым; N наличие макросов хоста. | Обязательные макросы диапазона/формата не имеют целевых определений и вычисления. |
| `ciso646` / `iso646.h`; совместимость 17 / токены 2 | Для альтернативных C++-токенов пустой фрагмент ожидаем. | C макросная граница; B использует or/not как C++-токены; лексические тесты относятся к языковому аудиту. | Это не отсутствующее семейство вызовов; C-подобные макросы не выдумываются. Одно распознавание заголовка не доказывает лексер. |
| `climits` / `limits.h`; 18 | Пустой фрагмент, пределы CHAR/INT/LONG не угадываются. | C оставляет INT_MAX неразрешённым; N наличие макросов. | Нет целевых определений/вычисления обязательных целочисленных пределов. |
| `clocale` / `locale.h`; 22 | Публичные поля lconv C90, char* setlocale(int,const char*), lconv* localeconv(). | C оба localeconv; N точные указатели; F набор объявлений. Непрозрачно. | Нет LC_*, изменения locale, значений/хранилища lconv, сгенерированного locale-сценария. |
| `cmath` / `math.h`; 26 | float/double/long-double; abs/fabs/sqrt и др.; pow(T,int) против pow(T,T), frexp/ldexp/modf. | C.`mathAndIntegerOverloadsSelectDistinctParameterTypes`, `cppStringSearchOverloadsPreserveConstness` (также pow/modf); N сигнатуры; B sqrt. Непрозрачно. | Нет HUGE_VAL и модели ошибок/точности/области/округления; C99 hypot/round исключены. |
| `csetjmp` / `setjmp.h`; 18 | Непрозрачный jmp_buf, void longjmp(jmp_buf,int); выбранный operand-bearing glibc setjmp backend. | Ограниченные API/CLI и optimized native проверки: [отдельный статус](cpp03-setjmp-verification.md). | Общие доказательства environment/lifetime/state, точные nonlocal CFG/DFG и другие runtime остаются открытыми; сохранено исходное ограничение ANY automatic objects. |
| `csignal` / `signal.h`; 18 | Непрозрачный целочисленный sig_atomic_t, void (*signal(int,void (*)(int)))(int), int raise(int). | C оба raise; F объявления; N указатели. Непрозрачно. | Нет номеров/handler-макросов, исполнения установки/доставки сигналов и модели асинхронных эффектов. |
| `cstdarg` / `stdarg.h`; 18 | Только непрозрачный va_list, без фиктивных функций va_start/va_arg/va_end. | C оба потребителя va_list через vprintf, макросная граница; N сигнатуры; B variadic-сумма хоста. | Нет va_*-раскрытия в переносимом CPG, модели жизни/обхода состояния; va_copy исключён как поздний. |
| `cstddef` / `stddef.h`; 18 | size_t по профилю; ptrdiff_t по политике знакового аналога size_t. | C.`cstddefTypesResolveUsingTheSelectedProfile`; D unsigned-long/unsigned-int; Q расстояние указателей. Только типы. | Нет независимого ABI-выбора ptrdiff_t, макросов NULL/offsetof. Парность типов — политика проекта, не требуемая ISO идентичность. |
| `cstdio` / `stdio.h`; 27 | Непрозрачные FILE/fpos_t; printf/scanf/variadic; файловый/символьный/блочный ввод-вывод, позиция/состояние/remove/rename. | C оба printf; N указатели; U.`cCompatibilityPointerOutputMayWriteExposedLocal` использует scanf; F отрицательный std-импорт. Непрозрачно. | Нет EOF/BUFSIZ/SEEK_* и стандартных stream-макросов, полной проверки формата/файлов/окружения и генерационной приёмки C I/O. gets оставлен как C++03-объявление, не рекомендация безопасности. |
| `cstdlib` / `stdlib.h`; 18/20/25/26 | div_t/ldiv_t, int/long abs/div, конверсии/память/процесс/random/qsort/bsearch/multibyte. | C оба abs/labs/atoi, точный int/long; N указатели; B div/abs; U различает значение и доступную память. Непрозрачно. | Нет EXIT_*/RAND_MAX/MB_CUR_MAX/NULL, моделей процессов/random/выделения/поиска; нет C99 long-long API. |
| `cstring` / `string.h`; 21 | memcpy/memmove/memset, строковые операции, mutable/const memchr/strchr/strpbrk/strrchr/strstr. | C оба strcmp/strchr и constness; N точные указатели; B strchr/strlen/strcmp. Непрозрачно. | Нет общей модели границ/перекрытия/терминатора/tokenizer/текста ошибки и отдельного CPG-теста каждой перегрузки. |
| `ctime` / `time.h`; 20 | Непрозрачные арифметические time_t/clock_t, поля tm; clock/time/difftime/mktime, конверсии/strftime. | C оба mktime; F.`demandedTimeTypeKeepsUnknownUnderlyingRepresentation`; N указатели. Непрозрачно. | Нет CLOCKS_PER_SEC/NULL, симуляции представления времени/часов/часового пояса/статических буферов. |
| `cwchar` / `wchar.h`; 21/27 | Непрозрачные mbstate_t/wint_t, wide I/O/строки/память/числа/время/restartable multibyte, mutable/const-поиск. | C оба wcslen; N сигнатуры; B wide length и указатель mbrtowc. Непрозрачно. | Нет WEOF/WCHAR_*/NULL, доказанного поведения conversion/locale/state и всех wide-перегрузок CPG. Исключение Clang/glibc для const-поиска описано ниже. |
| `cwctype` / `wctype.h`; 21/22 | wint_t, непрозрачные wctype_t/wctrans_t, wide-классификация/регистр/iswctype/wctype/towctrans/wctrans. | C оба wctype; N указатели; B iswdigit. Непрозрачно. | Нет модели дескрипторов/locale/исполнения классификации, определения WEOF; C99 iswblank исключён. |

[Уточнение C-проверок](../src/test/resources/library-catalogue/c-compatibility-coverage.md):
эта комбинация Clang/glibc предоставляет старые объявления широкого поиска.
Точный const wcschr проверяется GCC; поддержанный mutable-вариант — обоими.
Старый C-вход подавляет Clang-предупреждение расширения include-next, вызванное
CPATH. Это не второе независимое доказательство всех const-перегрузок.

## Положительные, отрицательные и граничные тесты по главам

«Отрицательный» означает реальное утверждение об отказе/невыборе цели.
Консервативный fallback и отсутствие макроса не доказывают стандартную
диагностику. «Не найден» — пробел, а не неявное прохождение. Программы с
неопределённым поведением не считаются положительными исполняемыми примерами.

| Глава | Положительные | Отрицательные | Границы и непокрытое |
|---|---|---|---|
| 17, введение | D заголовки/зависимости/объявления; L востребованное замыкание. | D исключает array/tuple/thread/unordered_map/cstdint/TR1; F не создаёт std-импорт из stdio.h. | L `includeAvailabilityBeginsAtTheDirective`, `includesDoNotLeakBetweenTranslationUnits`, `incompatibleProfilesKeepSeparateLibraryIdentities`; не все общебиблиотечные требования/свободы реализации. |
| 18, поддержка языка | R все 12 allocation-сигнатур, handlers/typeinfo/limits; C размерные типы. | C `c99AndLaterApisAreNotPresentedAsCpp03` исключает поздние API. D проверяет правильное размещение allocation-имён, а не отказ в неверном namespace; общая диагностика неверных программ не доказана. | C непрозрачное состояние и ограниченные подмножества va_*/setjmp, не полные обязательства; O неизвестные limits. Нет полной приёмки handler/nonlocal-jump исполнения. |
| 19, диагностика | R 9 конструкторов stdexcept; O what/иерархия. | C не выдумывает assert/errno. Отдельная нативная неверная программа этого семейства не найдена. | B assert хоста отдельно; CPG-макросы NDEBUG/errno/EDOM/ERANGE отсутствуют. |
| 20, утилиты | R pair-сравнения, allocator, functional. | U `ordinaryValueArgumentIsNotAReferenceToTheLocal`, `unevaluatedAddressDoesNotExposeStorage` запрещают ложные alias-эффекты; это не языковой отказ. | U alias результата allocator, O выбранные группы; нет полной семантики владения allocator/auto_ptr/raw-storage. |
| 21, строки | Q counted/NUL/traits/substr/append, C узкие/широкие вызовы, E строки. | Q `userStringSpecializationKeepsItsOwnMembers`, `lookalikeMethodsNeverAcquireLibrarySemantics` запрещают ложную стандартную семантику. | Q `substringCopyOverloadsNeverBecomeBufferCopies`, `materializationDoesNotAdmitNestedOrDiscardedStringTemporary`; не все исключения/псевдонимы. |
| 22, локализация | R 23 locale-вызова; O facet-группы; N numeric/time/money. | Отдельный неверный facet/locale CPG или нативный вход не найден; C исключает поздние character API. | F непрозрачные маски; нет модели locale/state/descriptor значений; сигнатуры не исполняют изменение locale. |
| 23, контейнеры | V/K вызовы/DFG/инвалидация; R bitset; E vector/семейства. | V `constIteratorKeepsConstElementsAndUnrelatedContainersDoNotCompare`; K `associativeConstIteratorsCannotBindWritableReferences`; недопустимая list-арифметика без целей. | V bool/нетривиальные сдвиги/ёмкость; K empty/end/условный splice/компараторы. Не все перегрузки/предусловия/гарантии исключений. |
| 24, итераторы | Q pointer/reverse distance, сохранённые адаптеры; S I/O-операторы; E строки/потоки. | S `iteratorDifferenceAliasDoesNotMergeUnrelatedBaseDeclarations`; V чужие итераторы; K категории. | U сохранённое/копированное/переприсвоенное I/O-состояние; Q неизвестный rebind сохраняет возможные записи. Законы итераторов/диапазонов проверены не полностью. |
| 25, алгоритмы | Q copy/find_if/transform/sort/remove; C qsort/bsearch-объявления; E строки. | Q `invalidCategoriesAndUnevaluatedCallsStayRaw`, `readOnlyRangesAndOutputsNeverAcquireMutationFacts`. Fallback не равен полному семантическому отказу. | Q remove не удаляет физически; функции могут менять globals; большинство перегрузок/сложность/исключения не покрыты отдельно. |
| 26, числа | R complex/valarray/limits; Q accumulate; C математика; B безопасные скалярные значения. | C нет hypot/round/long-long; отдельного отрицательного теста области функции/размерности valarray не найдено. | O неизвестные целевые константы; proxy/expression-представления и числовая точность/ошибки не моделируются. |
| 27, ввод-вывод | S состояние/манипуляторы/операторы; U псевдонимы; E `remaining-library` значения/EOF/fail/clear/повторная генерация. | S `setfillRetainsItsCharacterTypeAndDoesNotInventExtraction`; LibraryConsoleTest не нормализует ложные cout/endl. | Строковый поток не консоль; жизнь отброшенной возвращённой строки неполна; нет отдельной fstream/streambuf-приёмки. |

## Неопределённое, неуточнённое, зависящее от реализации и неподдержанное

Эти категории в отчётах нельзя объединять:

- **Неопределённое поведение (UB):** разыменование end/инвалидированного итератора,
  неверные диапазоны и размеры буфера, некоторые перекрытия и вызовы вне области
  допустимых аргументов нельзя использовать как переносимый исполняемый эталон.
  Сигнатурные входы N содержат заглушки диапазонов, неинициализированные указатели
  и null-указатели обработчиков исключительно для выбора сигнатур. Скрипт их не
  исполняет и не утверждает результата. Сохранение синтаксиса/цели в CPG не
  доказывает предусловия. Полное обнаружение UB не реализовано.
- **Неуточнённое поведение/представление (unspecified):** если стандарт допускает
  выбор, граф без доказательств не выбирает порядок хоста, layout proxy или
  представление результата адаптера. Непрозрачные идентичности манипуляторов,
  proxy и масок сохраняют эту границу. Непрозрачность может означать и отсутствие
  сведений; она не доказывает нормативную категорию каждого отдельного типа.
- **Определяемое реализацией (implementation-defined):** размерные/разностные
  типы, числовые диапазоны/форматы, представление времени/потоков и подобные
  документируемые выборы требуют профиля цели или определений. Непрозрачные типы
  не дают угадывать layout, но отсутствие обязательного макроса остаётся
  пробелом поддержки. Политика знакового аналога size_t для ptrdiff_t уже множества
  всех допустимых целей. Размер контейнера и коэффициент роста не предполагаются.
- **Ограничение реализации проекта:** неразрешённые обязательные макросы,
  отсутствующий тест, немоделируемая жизнь возвращённого объекта и неизвестные
  эффекты нельзя объявлять «неуточнёнными в ISO». Консервативные эффекты лучше
  ложных гарантий чистоты/alias/жизни и сами по себе не требуют отказа от
  корректного исходника.

## Воспроизводимые проверки и результат запуска

Рекомендуемые команды репозитория:

```sh
mvn -Dtest=StandardLibraryCatalogueTest,RemainingLibraryOverloadsTest,RemainingLibraryResolutionTest,CCompatibilityFamiliesTest,StreamLibraryResolutionTest,LibraryUnknownEffectsTest,LibraryCatalogueFamiliesTest,LazyLibraryGraphTest,LibraryConsoleTest,VectorResolutionTest,VectorIteratorResolutionTest,VectorAccessNormalizationTest,ContainerIteratorResolutionTest,ContainerNormalizationTest,ContainerFamilyFlowTest,StringAlgorithmResolutionTest,SequenceNormalizationTest,SequenceFlowTest,SequenceModelTest test
bash scripts/probe-cpp03-library-catalogue.sh
python3 scripts/check-cpp03-paragraphs.py
mvn verify
```

Запуск аудита 2026-11-06: Maven/javac в окружении отсутствовали. Основные и тестовые
исходники скомпилированы в отдельный временный каталог Eclipse ECJ с целью Java 21;
перечисленные 19 классов запущены JUnit Console с `-Xmx512m`.
**203 теста прошли, 0 ошибок, 0 пропусков**. Нативный скрипт **прошёл с GCC 14 и Clang 19**, включая обе
безопасные исполняемые программы. Для Clang использована документированная
настройка CPATH к GCC-заголовкам. Это целевые проверки, не новый полный
`mvn verify` и не все сценарии E. Полный результат принадлежит итоговому
сводному отчёту приёмки.

## Что нужно до заявления о полной поддержке

1. Разобраться с записанными различиями источника/профиля и явно перечислить
   принятые исправления; не смешивать чтение координат с приёмкой реализации.
2. Реализовать/профилировать обязательные макросы и добавить положительные,
   отрицательные и граничные CPG-тесты. Наличие макросов хоста не заменяет поддержку.
3. Расширить проверку точных перегрузок CPG за пределы выбранных R/O/V/K/Q/S,
   особенно файловые потоки, streambuf hooks и непрозрачные семейства.
4. Добавлять поведение и сгенерированное исполнение только там, где семантика
   действительно моделируется, включая исключения, корректность диапазонов и
   время жизни. В остальных случаях сохранять внешние эффекты консервативно.
5. Раздельно сообщать о каталоге, парсере, разрешении, эффектах, генерации и тестах.
   Ни одна строка аудита не даёт основания для безусловной отметки «полностью».


## Исправление после аудита: #90

Исходный аудит выше описывает каталог на момент проверки. В #90 добавлены
пропущенный заголовок strstream и четыре класса; текущий инвентарь содержит
51 основной заголовок и 69 написаний. Поведенческие пробелы D.7 и общая приёмка
#48 остаются открытыми. [Границы и воспроизводимые проверки](cpp03-strstream-verification.md).


## Исправление после аудита: #91

Описанный выше исторический пропуск устранён четырьмя шаблонами операторов во
вложенном `std::rel_ops`. Добавлены регрессии области поиска, выбора шаблонов,
ленивого владения ссылками и нативных таблиц результатов исходного/генерированного
кода. Каталог не содержит тел STL и не доказывает предусловия пользовательского
типа; 20.2.1/10 и общая приёмка сохраняют пробелы. [Проверки](cpp03-rel-ops-verification.md).

## Продолжение #92: макросы из профиля, первый пакет

Исторические пробелы выше закрыты частично: CPG предоставляет NULL, целочисленные
пределы, вещественные пределы явно выбранных форматов и WCHAR_MIN/MAX под обоими
написаниями заголовков. [Отдельный инвентарь](cpp03-library-macros.json) разделяет
54 реализованные пары «заголовок/имя» и 48 ещё открытых в #92. Наличие макроса
у нативного компилятора не считается реализацией CPG. [Отчёт](cpp03-library-macros-verification.md)
фиксирует целевые допущения, доказательства и оставшуюся работу над ABI/runtime/assert.
Этот пакет не означает полную поддержку библиотеки или закрытие #48.

## Продолжение #92: привязка assert

Исторический пробел cassert теперь имеет собственный функциональный макрос CPG,
переключение NDEBUG, явную привязку glibc и диагностику недоступной привязки для
прочих профилей. Это не переносимая реализация libc и не доказательство всех
исторических типов аргумента. Копирование и независимые правки поддерживаются;
правки условия через раскрытый граф отклоняются до безопасного повторного
получения строки диагностики. [Проверки](cpp03-assert-verification.md).

## Продолжение #92: целочисленные факты платформы

Исторические строки об отсутствующих значениях выше теперь частично закрыты:
30 целочисленных объектных макросов получают типы и значения из явного профиля.
Пропущенные факты остаются недоступными. Это не реализует состояние errno,
смену locale, доставку сигналов, стандартные потоки, генерацию rand или завершение
процесса. Текущий инвентарь разделяет 54 определения из профиля, одну привязку
assert, 30 целевых фактов и 17 ещё открытых обязательств.
[Проверки и целевые предпосылки](cpp03-runtime-integer-macros-verification.md).

## Продолжение #92: изменяемый errno

Исторический пропуск errno теперь закрывается явной glibc-привязкой к реальной
accessor-функции и объектным макросом, дающим изменяемое lvalue. Числовые коды
ошибок выбираются отдельно. Неизвестный runtime диагностируется при использовании,
а не заменяется выдуманным глобальным int или нулём. Точная идентичность TLS и
полная семантика изменения ошибок всеми функциями остаются за границей пакета.
[Проверки](cpp03-errno-verification.md).

## Продолжение #92: текущий многобайтовый предел

Историческая строка об отсутствии MB_CUR_MAX дополнена ограниченной явной
привязкой функции glibc, отдельно от прежних числовых макросов и фактов профиля.
Оба заголовка сохраняют объектный макрос и обычные объявления. Запрос возвращает
неизвестный скалярный rvalue, а не константную модель локали. Точный size_t
относится к выбранному glibc-контракту, а не исходному тексту C90. Старые таблицы
и числа отражают прежние этапы; на пятом этапе остаток составлял 15 обязательств/14 имён.

Поддержан только явно проверяемый контракт glibc-x86_64-lp64-mb16; одного имени
glibc недостаточно для ABI или включения запроса. Полная семантика преобразований
локали и общие диагностики недопустимых операций над скалярными результатами
вызовов остаются вне пакета. #92 и #48 открыты. [Проверки](cpp03-mb-cur-max-verification.md).

## Продолжение #92: стандартные потоки

Исторический пробел макросов stdin/stdout/stderr теперь имеет явную ограниченную
привязку. Точные самоссылочные макросы и реальные глобальные FILE* с C linkage
используют канонический непрозрачный FILE и совместимую внешнюю идентичность,
сохраняя происхождение из заголовков. Недоступность по умолчанию и выбранный
LP64-контракт отделены от переносимых гарантий выражений исходного C90.

На шестом этапе реестр содержал 90 обязательств/84 имени в заданных границах;
12 обязательств/11 имён остаются открытыми. Прошли 18 новых unit-тестов и три
нативных сценария GCC/Clang, включая материализацию раскрытий CPG и
копированные/изменённые/преобразованные исходники. Полный набор тестов и CI
точного коммита остаются отдельными ожидающими проверками. Не заявляются layout
FILE, постоянные адреса/дескрипторы, полная семантика буферов/позиций/ошибок/
закрытия или общая диагностика квалифицированных имён. Старые таблицы и отчёты
сохраняют историческую область проверки.
[Доказательства и оставшиеся пробелы](cpp03-standard-streams-verification.md).
#92 и #48 открыты.

## Настраиваемые wint_t и WEOF (#92, седьмой этап)

Объект `wideInteger` совместно задаёт `wintType`, десятичную строку `weof` и
`characterDomain: "nonnegative-wchar"`. Отсутствующие или пустые настройки
сохраняют непрозрачный wint_t и недоступный WEOF; реальные заголовки имеют
приоритет. Поддерживаются int, unsigned int, long и unsigned long. Обе семьи
широких заголовков, сигнатуры и char_traits используют выбранный канонический
тип. Факты не выводятся из текущей машины или семейства libc.

Тип должен покрывать весь неотрицательный диапазон wchar_t; WEOF должен
помещаться в тип и лежать вне этого диапазона. Эта намеренно более строгая
политика не является требованием ISO к репертуару символов. Допустим -1,
в том числе представимый в wchar_t; равенство EOF не требуется. Константные
выражения сохраняют тип и работают в #if. Синтетический профиль не доказывает
совместимость ABI нативных wide-вызовов или полную модель символов и локали.

На седьмом этапе реестр содержал 92 обязательства/85 имён в заданных границах; 10 обязательств/
10 имён остаются открытыми. Целевые проверки поддерживают две записи WEOF;
четыре нативных WEOF-сценария прошли. Остальные регрессии, полный набор и CI учитываются
отдельно в [отчёте](cpp03-weof-verification.md). Прежние числа и отчёты остаются
историческими. #92 и #48 открыты.

## Целевые факты масштаба часов (#92, восьмой этап)

Необязательный `clockScale` совместно задаёт независимые `clockType`,
`expressionType` и положительную десятичную строку `value`. Каждый тип может
быть int, unsigned int, long или unsigned long; значение должно помещаться
в expressionType, но не обязательно в clockType. Без настроек clock_t остаётся
непрозрачным, CLOCKS_PER_SEC недоступен. Реальные заголовки имеют приоритет;
факты не выводятся из libc, JVM или текущей машины.

Исходный C90 описывает арифметический clock_t и число единиц clock() в секунду,
не требуя позднейшего точного типа clock_t или константной формы макроса.
Выбранный типизированный литерал работает в #if; это не универсальная
историческая гарантия. clock() остаётся непрозрачным вызовом runtime.
На восьмом этапе реестр содержал 93 обязательства/86 имён в заданных границах, девять обязательств/
девять имён открыты. Прошли 269 unit-тестов и 28 интеграционных тестов;
полный набор и CI учитываются отдельно в [отчёте](cpp03-clock-scale-verification.md).
Прежние числа остаются историческими. #92 и #48 открыты.

## Явная возможность компилятора для HUGE_VAL (#92, девятый этап)

`hugeVal: {kind: "positive_infinity", emission: "gcc-clang-builtin-huge-val"}`
выбирает закрытое выражение положительной бесконечности double при явном
формате binary64 и 64-битном хранении. Без настроек оно недоступно; libc,
текущая машина или одна ширина не включают возможность. Реальные заголовки
сохраняют приоритет. Точный builtin без аргументов разрешён и при повторном
разборе под тем же профилем. Обычные объявления cmath не меняются.

CompilerIntrinsicExpression хранит символические double/rvalue и JSON,
без выдуманного вызова libc, числового JSON Infinity или конечной подмены
BigDecimal. Общий вычислитель floating, конечная политика HUGE_VAL и полная
IEEE-арифметика не добавлены. Исходный C90 допускает бесконечность, не требуя
константной формы. Присваивание/инкремент и недопустимые объявления/квалификация
отклоняются; адрес вычисленного rvalue сохраняет существующий пробел диагностики
CPG, хотя строгие нативные компиляторы его отклоняют. Целочисленный #if не обещан.

Прошли 432 unit-теста и 31 IT, отдельно восемь unit-тестов (440 различных). На девятом этапе реестр содержал 94 обязательства/
87 имён в заданных границах, восемь обязательств/восемь имён открыты. Полный
набор и CI учитываются отдельно в [отчёте](cpp03-huge-val-verification.md).
Прежние числа исторические; #92 и #48 открыты.

## Явные диспозиции сигналов (#92, десятый этап)

`signalDispositionBinding: "glibc-x86_64-lp64"` задаёт SIG_DFL/SIG_IGN/SIG_ERR
явными приведениями 0/1/-1 к void(*)(int). По умолчанию привязка недоступна
независимо от cRuntime. Требуются glibc и 8-битный байт / 32-битный int / 64-битные long и указатели, без
связи с sizeType или MB_LEN_MAX. Реальные заголовки имеют приоритет. Указатели
сентинелов отделены от положительных целых номеров сигналов; выдуманная функция,
целочисленное вычисление указателя или гарантия #if не добавляются.

Прошли 313 unit-тестов и 22 интеграционных теста с материализацией CPG и безопасным
сохранением/сменой/восстановлением диспозиций в дочернем процессе без доставки
сигналов. Нативный SIG_ERR с typedef-приведением случайно проходит проверенный
#if; все выбранные явные приведения указателя его отклоняют. Эквивалентность
препроцессинга не заявляется. Пробелы language-linkage в FunctionType и C++ linkage
каталожного signal остаются; полная ISO-семантика callback и асинхронная модель
не заявляются. Реестр содержит 97 обязательств/90 имён в заданных границах,
пять обязательств/пять имён открыты. [Проверки и ожидающие этапы](cpp03-signal-dispositions-verification.md).
Прежние числа исторические; #92 и #48 открыты.

## Подготовка POD/членов/layout для offsetof (#92)

Внутренний PodLayout консервативно проверяет POD, пути членов и рекурсивный
layout с union по существующим явным фактам профиля. Результат обоснован или
содержит типизированную причину отказа, без догадок по текущей машине. Запрос
отделён от общей проверки aggregate и не расширяет sizeof. Layout enum,
указателей на члены, шаблонов и границы массивов с sizeof(union) остаются вне
набора; допустимые случаи C++03 не объявляются ошибками языка.

Выход за представимый диапазон size_t даёт SIZE_NOT_REPRESENTABLE.
Эта необходимая граница не доказывает максимальный размер объекта компилятора.
Прошли 226 unit-тестов и пять IT, включая независимый нативный оракул layout и
значимые правки полей. Нативный offsetof не доказывает поддержку макроса CPG.
Новые макрос, builtin, parser или node не включены; offsetof открыт и недоступен.
Числа прежние: 97 обязательств/90 имён в заданных границах, пять обязательств/
пять имён открыты. [Проверки и оставшиеся этапы](cpp03-offset-layout-verification.md).
#92 и #48 открыты; старые отчёты сохраняют историческую область проверки.

## Выбранный builtin offsetof (#92 остаётся открытой)

Объект `offsetof` явно задаёт emission gcc-clang-builtin, layout
x86_64-sysv-lp64-v1 и положительную десятичную границу supportedObjectBytes
не больше 2147483647. Без настроек возможность недоступна. Точные размеры,
выравнивания, size_t и natural-layout проверяются независимо от libc и формата
floating. Граница действует на зависимости и задаёт программный набор,
а не универсальный максимум компилятора.

OffsetofExpression владеет типом и путём членов/индексов. ICE-запросы вычисляют
проверенные значения; folding сохраняет всё выражение и транзитивные границы
layout/const-инициализаторы для правок. При вычислении или генерации offsetof активные pragma
и развёрнутые pragma-операторы отклоняются, кроме точного pragma once;
неактивные директивы допустимы. Не-POD sizeof-зависимость означает неподдержанный
случай, а не недопустимого владельца. Защита повторного разбора не ослабляется.

Прошли 325 unit-тестов и 15 IT; повторные восемь unit-тестов и два IT уже входят
в эти числа. Нативные проверки используют материализацию CPG. Допустимые POD
enum/member-pointer/template и случаи sizeof-union остаются вне набора;
offsetof открыт/частичен. Числа прежние: 97 обязательств/90 имён в заданных
границах, пять обязательств/пять имён открыты. Предыдущий раздел подготовки
исторический. [Проверки и оставшиеся этапы](cpp03-offsetof-builtin-verification.md).
#92/#48 открыты; полный Maven и CI точного коммита проверяются отдельно.


## Ограниченные контексты offsetof (#92, partial)

Текущий этап расширяет выбранный запрос на уже материализованные полные
конкретные POD-инстанцирования шаблонов и локальные зависимости sizeof, включая
объединения. Данные об основном шаблоне, явных и частичных специализациях
относятся к фактически выбранным конкретным инстанцированиям, а не к общей
поддержке шаблонов. Явная модель размещения и предел размера не меняются;
поведение общего sizeof и утверждения о поддержке ABI не расширяются.

Реализация сохраняет записанные аргументы типов и значений, значения по умолчанию
и значимые зависимости заголовков специализаций при свёртке. Поэтому правки
могут повторно выбрать инстанцирование и проверить предел. Сохраняются проверки
владения снимком, циклов, размера, pragma и операторов, а также воспроизведения
исходника. Именованные аргументы-значения и зависимые значения по умолчанию,
которые общий механизм не может инстанцировать, остаются ограничениями
предварительного этапа. Зависимости sizeof от не-POD типов остаются допустимыми,
но неподдерживаемыми случаями, а не общими ошибками языка.

Исправленный снимок прошёл все 1411 unit-тестов и 13 выбранных IT за 8:50.
Независимые 78 unit-тестов и два IT перекрывают этот набор. Полный Maven
и CI остаются отдельными незавершёнными проверками. Копирование синтаксиса с сохранением
идентичности исправляет владение typedef в аргументах; производные
инстанцирования сохраняют псевдонимы аргументов вызывающего контекста, исключая
заполнители основного шаблона и каталога, а также псевдонимы с каноническим
целевым типом OpaqueLibraryType. Зависимые или неразрешённые цепочки
целевых типов typedef остаются неподдерживаемыми. Аргументы через typedef
массива больше не исключены. Псевдонимы членов с зависимыми целевыми типами и аргументы типа указателя
на функцию сохраняют ограничения общего механизма; разрешённые псевдонимы
пространств имён проверены успешно.
Независимые проверки не прибавляются к числам расширенного прогона. offsetof остаётся
open/partial: 97 обязательств / 90 имён с ограниченной поддержкой и пять открытых
обязательств / пять имён. Предыдущий раздел о builtin описывает исторический этап.
[Текущие данные и проверки](cpp03-offsetof-contexts-verification.md).
#92 и #48 остаются открытыми.


## Выбранное хранение указателей на члены для offsetof (#92, partial)

Необязательная настройка memberPointerStorage="itanium-x86_64-lp64-v1" отдельно
задаёт размер/выравнивание 8/8 байт для указателя на поле и 16/8 для указателя
на функцию-член. Указатель на поле, само являющееся указателем на функцию,
использует 8/8. Без настройки поддержка не включается; ширина обычного указателя,
glibc и свойства хоста её не определяют. Значения, кодирование и вызовы через
указатели на члены не моделируются.

Тела известных неполных, не-POD или слишком больших классов-владельцев, а также
классов параметров и результата не требуют расчёта размещения или бюджета.
Однако записанный синтаксис типов, сигнатур и шаблонов должен разрешаться;
зависимости sizeof и констант сохраняются при свёртке и проверяются по пределу.
Правка с превышением предела отклоняется. Одной идентичности владельца
недостаточно для безусловной поддержки. Хранение enum и битовые поля остаются
вне этапа; исходная семантика C++03 для enum не меняется.

Чистый прогон прошёл все 1422 unit-теста и 15 явно выбранных IT за 09:54
без ошибок и пропусков, включая 44 новые native-записи. Независимые
11 unit-тестов и два IT перекрывают этот набор и не суммируются. Полный Maven
и CI остаются отдельными незавершёнными проверками. offsetof остаётся open/partial: 97 обязательств /
90 имён с ограниченной поддержкой и пять открытых обязательств / пять имён.
[Текущие данные и проверки](cpp03-member-pointer-layout-verification.md).
Предыдущие разделы описывают исторические этапы. #92 и #48 остаются открытыми.


## Ограниченное хранение enum для offsetof (#92, partial)

Независимая настройка enumStorage="gcc-clang-x86_64-default-enum-v1" выбирает
представление 4/4 или 8/8 байт после проверки допустимости исходного диапазона.
Без настройки enum не поддерживается; выбор указателей на члены её не включает.
Исходное продвижение по полному диапазону базового типа и ограничение смешанного
диапазона сохраняются. Современный компилятор даёт сравнение, а не новое правило.

Хранение отделено от арифметических и типовых зависимостей: для последних нужно
доказать совпадение исходного и native-продвижения. Типы лексических
инициализаторов и идентичность enum в условных выражениях должны сохраняться;
свёртка удерживает фактические типовые зависимости без вычисления
runtime-инициализаторов. Общая семантика типов не меняется. Битовые поля
и оставшиеся предпосылки вне этапа.

Чистый прогон прошёл все 1439 unit-тестов и 22 выбранных IT за 19:34 без
ошибок и пропусков. Независимые 79 проверок и 68 JUnit-тестов перекрываются
с ним и не суммируются. Проверены 82 native-записи и 40 материализаций CPG.
Полный Maven и CI остаются отдельными незавершёнными этапами. Числа прежние: 97 обязательств /
90 имён с ограниченной поддержкой и пять открытых обязательств / пять имён.
offsetof остаётся open/partial; #92 и #48 открыты. Предыдущие разделы исторические.
[Текущие данные](cpp03-enum-storage-verification.md).


## Именованные целые аргументы шаблонов для offsetof (#92, частично)

Разрешённые const-имена целого типа и перечислители могут задавать представимые
целые аргументы шаблона класса и значения полной специализации. Факты принадлежат
снимку; нормализация сохраняет тип параметра, а bool использует преобразование
истинности. Написанные аргументы и цепочки инициализаторов сохраняются для
ограниченной проверки размещения и последующего редактирования. Новый ABI-профиль
не вводится.

Непредставимые преобразования, недоказанные шаблоны специализаций и оставшиеся
шаблонные зависимости явно не поддерживаются; это не утверждение, что все
исключённые программы нарушают исходный C++03. Продвижение enum, предел размера
и необязательные политики представления не меняются. Пять оставшихся макро-
обязательств не завершены; счётчики завершённых макросов не увеличиваются.
[Границы и проверки](cpp03-constant-template-arguments-verification.md).


## Именованные целые значения частичных специализаций (#92, частично)

Независимые целые константы и перечислители верхнего уровня в аргументах
частичных специализаций используют факты снимка при сопоставлении, упорядочении
и объединении повторных объявлений. Записанные заголовки и цепочки
инициализаторов, включая невыбранных кандидатов, сохраняются для повторного
выбора после правок, копирования и свёртки. Идентичность указателей/ссылок
не изменяется. Вложенные именованные шаблоны, зависимые типы параметров и
непредставимые знаковые преобразования не сертифицируются.
Дополнение для bool отображает ноль в false, любое ненулевое целое значение
в true при сопоставлении, упорядочении и объединении повторных объявлений.
Записанные зависимости сохраняются после копирования, свёртки и правок.
[Проверки преобразований в bool](cpp03-boolean-partial-arguments-verification.md).
Профиль, ABI, счётчики макросов и итоговая приёмка не расширены.
[Текущие проверки](cpp03-partial-constant-template-arguments-verification.md).
Предыдущие разделы описывают исторические пакеты; #92 и #48 остаются открытыми.

### Преобразования аргументов шаблонов в unsigned (#92, ограниченный пакет)

Проверенные целые факты преобразуются в беззнаковый тип по модулю 2^N, где N
задаёт профиль. Единое правило действует для обычных аргументов, значений по
умолчанию, полных специализаций и частичных шаблонов верхнего уровня, включая
сопоставление, упорядочение и повторные объявления. Bool сохраняет правило
ноль/неноль. Непредставимые знаковые преобразования, вложенные именованные
шаблоны и зависимые типы параметров частичных специализаций не поддерживаются.
Записанные зависимости, проверки циклов/владения, пределы размера и
совместимость enum не изменены. ABI и счётчики макросов не расширяются;
#92 и #48 остаются открытыми. [Границы и гейты](cpp03-unsigned-template-arguments-verification.md).

### Конкретные типы параметров частичных специализаций (#92, ограниченный пакет)

Целый тип параметра частичного шаблона верхнего уровня может определяться
предыдущими аргументами самой специализации: например, первичный шаблон
`template<class T, T N, class U>` и частичная специализация
`<unsigned char, fixed, U>`. Единое преобразование фактов снимка используется
при сопоставлении, упорядочении и повторных объявлениях. Типы из места
использования не подставляются для доказательства зависимого шаблона.
Сохраняются записанные зависимости, типы в теле, идентичность указателей/ссылок,
исходная семантика enum и ограничения размера/владения/циклов. Вложенные
именованные шаблоны, остающиеся зависимыми типы параметров и непредставимые
знаковые преобразования остаются неподдерживаемыми. ABI и число завершённых
макросов не меняются; #92 и #48 открыты. [Границы и гейты](cpp03-concrete-partial-parameter-types-verification.md).

### Вложенные целые значения частичных шаблонов (#92, ограниченный пакет)

Независимые именованные целые значения нормализуются в явно записанных
аргументах вложенного class-template-id частичной специализации, например
`Box<Key<T, fixed> >`. Имя вложенного шаблона должно быть связано с первичным
объявлением класса либо проверенным исходным шаблоном экземпляра; совпадения
написания недостаточно. Целый тип параметра должен определяться предыдущими
аргументами самого вложенного шаблона. Типы из места использования не
доказывают конкретность зависимого шаблона. Нормализация сохраняет идентичность
объявлений, параметров и typedef; действуют прежние правила bool,
беззнакового модуля и представимых знаковых значений. Записанные заголовки
и цепочки инициализаторов остаются зависимостями после копирования, свёртки
и правок. Неразрешённые/зависимые/template-template имена, пропущенные вложенные
аргументы по умолчанию, зависимые типы параметров, арифметическое выведение
и непредставимые знаковые преобразования остаются вне пакета. Идентичность
указателей/ссылок, совместимость исходных enum и пределы размера/циклов/владения
не меняются. Прежние исключения вложенных шаблонов описывают исторические
пакеты. ABI и число завершённых макросов не расширяются; #92 и #48 открыты.
[Границы, тесты и гейты](cpp03-nested-partial-arguments-verification.md).

### Целые defaults вложенных actual-аргументов (#92, ограниченный пакет)

Пропущенный хвост независимых целых значений actual template-id берётся только
из его собственного связанного первичного заголовка, видимого до использования,
либо проверенного origin экземпляра. Сопоставление и идентичность внешнего
экземпляра используют один список эффективных аргументов, сохраняя исходный
синтаксис и зависимости defaults. Поддерживаются literal/const/enumerator,
bool, unsigned по модулю и представимые signed значения; тип может стать
конкретным из собственных предыдущих явно заданных аргументов.
Пропуски в partial patterns, слияние/поздние defaults, зависимые/type/template
defaults и неподтверждённые имена остаются под диагностикой. Прежние ограничения
пропусков отражают исторические этапы. ABI, число макросов, аудит раздела 11
и статус завершения не меняются: #92/#48 и пять обязательств остаются открытыми.
[Контракт, тесты и отдельные гейты](cpp03-nested-actual-defaults-verification.md).


### Независимые целые defaults во вложенных partial patterns (#92)

Вложенный primary template-id в частичном pattern может опускать независимые
целые trailing defaults: например, `Box<Key<T> >` при первичном
`template<class T, int N=2> struct Key`. Default принадлежит точному связанному
первичному заголовку (либо проверенному instance origin), видимому в точке
объявления pattern, а не позднего outer use. Тип value parameter должен быть
конкретным из собственных предыдущих явно написанных аргументов pattern;
подстановка actual use не служит доказательством. Общий effective list нужен
для сопоставления, порядка, повторных объявлений и repeated deduction без
переписывания исходного синтаксиса. Исходные const/sizeof/enum зависимости всех
кандидатных partial headers, включая невыбранные, сохраняют layout/folding
ограничения: преобразование в bool или малое unsigned их не устраняет.

Ранние исключения omitted patterns описывают исторические пакеты. Dependent,
type/template defaults, слияние defaults повторных объявлений, поздние defaults,
неразрешённые/dependent/template-template heads, arithmetic deduction и signed
out-of-range остаются вне новой границы. C++03 допускает слияние defaults,
но этот subset его не сертифицирует. Defaults в списке параметров самой partial
specialization запрещены отдельно. Прежние несвязанные default routes сохранены.
#92/#48 и пять macro obligations открыты; section 11 и ABI policy не изменены.
[Свежая сверка оригинала, необходимые assertions и отдельные гейты](cpp03-nested-partial-defaults-verification.md).


### Вложенные defaults встроенных целых типов (#92, ограниченное продолжение)

Вложенные actual и partial-pattern class-template-id допускают пропуск явно
записанных независимых defaults встроенных целых типов. Для
`template<class T, class V=unsigned char, V N=258> struct Key` формы `Key<T>`,
`Key<T,unsigned char>` и `Key<T,unsigned char,2>` эквивалентны при восьмибитном
unsigned char выбранного профиля. Включён и чистый type default `class V=int`.
Тип следующего value parameter определяется собственными предыдущими effective
arguments, включая эти type defaults; независимое выражение value default
по-прежнему вычисляется на исходном связанном узле. Cv-идентичность аргумента
типа сохраняется: проверка целочисленности не склеивает `const int` и
`volatile int` с `int`.

Требуются точный связанный primary и его видимость в точке actual use или
объявления partial pattern. Сопоставление, порядок, идентичность повторных
объявлений, repeated deduction и экземпляров используют contextual effective
arguments без изменения написанных AST/source/JSON. Зависимости выбранных и
невыбранных кандидатов остаются под прежними проверками. Этот маршрут не
добавляет defaults типов typedef/class/enum/pointer/reference/array/function/
template, зависимые выражения/типы, merged/later defaults, неподдержанные heads,
signed out-of-range conversions или arithmetic deduction. Это границы
реализации, не запреты C++03; прежние ordinary routes сохраняются. Ранние
исключения всех type defaults относятся к историческим пакетам. #92/#48 и пять
macro obligations остаются открытыми; section 11 и ABI policy не меняются.
[Контракт, свежая сверка оригинала и отдельные гейты](cpp03-nested-integral-type-defaults-verification.md).

### Связанные integral typedef defaults (#92, ограниченный шаг)

Вложенные actual/partial class-template-id принимают TYPE default, уже связанный
с конечной global/namespace typedef-цепочкой текущего snapshot до встроенного
целого типа. Для `cfg::Byte`, обозначающего `unsigned char`, варианты `Key<T>`,
`Key<T,cfg::Byte>` и `Key<T,unsigned char,2>` согласованы при `V=cfg::Byte` и
независимом `V N=258` в профиле с восьмибитным char. Исходный аргумент, связи и
cv-идентичность сохраняются; нового lookup в точке использования нет. Видимость
точного primary header и собственные preceding effective arguments сохраняют
свои ограничения; общие узлы defaults не смешивают разные контексты.

Member/local/foreign/cyclic/unresolved/dependent и нецелые aliases, merged/late
headers, зависимые defaults и unsupported heads не добавлены. Скрытые sizeof/enum
зависимости выбранных и невыбранных кандидатов и ordinary routes сохранены.
Прежние общие запреты typedef относятся к историческим этапам. Это ограниченный
prerequisite offsetof/POD, не полная поддержка templates. Section 11/access и ABI
политики не изменены; #92/#48 и пять macro obligations остаются открытыми.
[Контракт, assertions и отдельные гейты](cpp03-nested-integral-typedef-defaults-verification.md).

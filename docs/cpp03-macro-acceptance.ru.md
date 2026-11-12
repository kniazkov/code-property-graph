# Обязательные библиотечные макросы: согласованная приёмка #92

## Область и правило решения

База: master `d2dfb88a6556393e08f2b1b4b1604901826c2d02` после PR #144.
Целевые тесты, полный clean Maven verify и независимое review прошли, как
указано ниже. Exact-head обязательный CI и post-merge проверка пока ожидаются.
**102 обязательства header/name, 95 имён: 54 profile_definition,33 target_fact,
9 runtime_binding,1 compiler_intrinsic,5 partial/open**. Классификации сохранены.
Наличие определения не доказывает семантику; 97 реализаций по классификации
не означают 97 универсально доказанных контрактов.

[#92](https://github.com/kniazkov/code-property-graph/issues/92) требует поддержки
**либо открытой ссылки на остаточную работу** для каждого макроса, собственных
раскрытий CPG, явных target/ABI-границ и воспроизводимых проверок. Полная C-библиотека,
все ABI и полное соответствие C++03 не требуются. #48 остаётся открытой.
Пять partial-обязательств не меняют статус даже при последующем закрытии
координационной задачи с отдельно отслеживаемыми остатками.

## Матрица критериев #92

| Критерий | Конкретное свидетельство | Граница / новый гейт |
|---|---|---|
| Точный инвентарь исходной редакции | 102 строки ниже; validators и сравнение реальных определений P | Учёт имён/локаторов не доказательство поведения; без C99/C++11 API |
| Явные target NULL/целые/float пределы | I/F/N/W; два профиля и отказ неизвестного формата | ALL54 и NUM48 прошли focused-приёмку; без универсальной numeric ABI гарантии |
| assert/NDEBUG/reinclude и невыполнение отключённого аргумента | A; `AssertionMacrosTest#reinclusionSelectsPresenceAndDoesNotReactUntilNextInclude`; native A и failure-тест | ASSERT18 прошёл focused-приёмку с фактическими подстановками; host assert не заменяет CPG |
| Макро-природа функциональных/целевых средств и явный ABI | R/E/M/S/D/H/C/WE и partial O/V/J | Без фиктивной функции вместо setjmp и универсальных runtime/ABI обещаний |
| Поддержка либо отдельная открытая работа каждого обязательства | 97 классифицированных реализаций, пять OPEN | Открытые остатки #145 offsetof, #146 cstdarg, #147 setjmp; пять обязательств partial |
| #if INT_MAX/выражения, два профиля, старые заголовки | I/N/W/R; `LibraryMacrosIT#profilesProvenanceApiCliCopyAndEditedGeneration` | Прежний numeric native сохраняет host headers; новый NUM48 oracle прошёл focused |
| TU и приоритет физического заголовка | `StandardLibraryMacrosTest#headerVisibilityRepeatedInclusionAndTranslationUnitsAreIndependent`, `#actualSuppliedHeadersTakePrecedenceOverTheBuiltinMacroModel`; изоляция R | Дополняется provenance/visibility тестами семейств, не общим runtime-доказательством |
| API/CLI/JSON, копия/правка/reanalysis/генерация | Точные native-методы ниже; ошибки блокируют генерацию | Счётчики/hashes limits/assert проверены; independent review пройдено |
| Свежий исполняемый файл и точные assertions | Эта карта методов/102 строк; старые packet-отчёты исторические | Свежий JAR SHA/full verify записаны ниже; exact-head CI/post-merge pending |
| RU/EN, матрица и mutable ledger | Этот отчёт и английская версия; прочие изменения ведёт координационный пакет | Наличие тестов не повышает immutable hashes/classifications |

## Ключи свидетельств: точные методы и assertions

Проверены текущие методы/именованные assertions, но это не утверждение об их
успешном запуске в этой приёмке. P применяется к доступным классифицированным
определениям; partial V/J имеют отдельные capability-тесты и не следуют лишь из P.

### P

[`StandardLibraryMacrosTest#implementedInventoryObligationsMatchActualHeaderDefinitions`](../src/test/java/com/kniazkov/cpg/parser/StandardLibraryMacrosTest.java): Множества доступных/недоступных определений, алиасы заголовков и capability; НЕ доказательство значений.

### I

[`StandardLibraryMacrosTest#integerLimitsWorkInDirectivesAndOrdinaryExpressionsForTwoProfiles`](../src/test/java/com/kniazkov/cpg/parser/StandardLibraryMacrosTest.java): Точные INT_MAX/LONG_MAX/ULONG_MAX/CHAR_MIN/CHAR_MAX/MB_LEN_MAX в двух профилях; INT_MIN<0 и UINT_MAX>INT_MAX в #if. Прочие имена здесь проверены лишь на наличие.

### F

[`StandardLibraryMacrosTest#floatMacrosUseExplicitFormatsNotStorageWidthsOrJavaFloatingPoint`](../src/test/java/com/kniazkov/cpg/parser/StandardLibraryMacrosTest.java): FLT_RADIX=2; мантиссы24/53/113 или64, округление1 или0; токен FLT_EPSILON1.1920928955078125e-7F. Все29 имён, но без независимых значений остальных констант. Неизвестный формат отклоняется.

### N

[`StandardLibraryMacrosTest#headerVisibilityRepeatedInclusionAndTranslationUnitsAreIndependent`](../src/test/java/com/kniazkov/cpg/parser/StandardLibraryMacrosTest.java): Все семь поставщиков NULL и старые написания раскрываются в выбранный0L; отсутствие до include и восстановление после undef/reinclude.

### W

[`StandardLibraryMacrosTest#wcharLimitsUseWcharIntegralPromotionRatherThanUnderlyingIntegerRank`](../src/test/java/com/kniazkov/cpg/parser/StandardLibraryMacrosTest.java): wchar с long32 даёт (-2147483647-1),2147483647; unsigned long32 даёт0U,4294967295U: проверено продвижение, не ранг хранения.

### R

[`RuntimeIntegerMacrosTest#exactThirtyNamesBothAliasesAndTargetValuesInConditionalsAndExpressions`](../src/test/java/com/kniazkov/cpg/parser/RuntimeIntegerMacrosTest.java): Все30 явно заданных int-фактов: #if против выбранного manifest, обычное раскрытие и алиасы заголовков; значения являются конфигурацией, не универсальными libc-константами.

### RV

[`RuntimeIntegerMacrosTest#validationSeparatesHistoricalConstraintsFromBoundedIntPolicy`](../src/test/java/com/kniazkov/cpg/parser/RuntimeIntegerMacrosTest.java): Отклоняются нулевые errno-коды, неположительные сигналы, неотрицательныйEOF, малые BUFSIZ/FOPEN_MAX/TMP_MAX/RAND_MAX, неверные/непредставимые факты и запрещённые дубликаты.

### A

[`AssertionMacrosTest#disabledArgumentsAreNeitherPrescannedNorSemanticallyRetained`](../src/test/java/com/kniazkov/cpg/parser/AssertionMacrosTest.java): Аргумент отключённого assert не раскрывается и не сохраняется семантически; отдельный reinclusion-тест проверяет изменение NDEBUG лишь при следующем include.

### E

[`ErrnoRuntimeTest#bothHeadersExposeWritableIntLvalueThroughRealGlobalLibraryHelper`](../src/test/java/com/kniazkov/cpg/analysis/ErrnoRuntimeTest.java): Оба заголовка связывают __errno_location; разыменование имеет int и неизвестное значение; запись/инкремент/ссылка/адрес разрешаются без фиктивного extern int errno.

### M

[`MbCurMaxRuntimeTest#queryIsUnknownRvalueAndSizeofDoesNotCall`](../src/test/java/com/kniazkov/cpg/analysis/MbCurMaxRuntimeTest.java): Результат locale-зависимого helper остаётся неизвестным rvalue; sizeof имеет значение без вычисляемых эффектов helper.

### S

[`StandardStreamsRuntimeTest#bothHeadersExposeGlobalMutableOpaquePointersAndPreserveOrdinaryFunctions`](../src/test/java/com/kniazkov/cpg/analysis/StandardStreamsRuntimeTest.java): Оба заголовка дают изменяемые глобальные указатели на opaque FILE; обычные функции разрешаются без выдуманной раскладки FILE.

### D

[`SignalDispositionsRuntimeTest#castsHaveExactHandlerTypeWithoutFabricatedCalls`](../src/test/java/com/kniazkov/cpg/analysis/SignalDispositionsRuntimeTest.java): Все3 значения имеют тип pointer(function(int)->void), rvalue и неизвестный указатель; вызовы только явно записанных signal/raise.

### H

[`HugeValRuntimeTest#floatingArithmeticAndConversionsStayConservativelyUnknown`](../src/test/java/com/kniazkov/cpg/analysis/HugeValRuntimeTest.java): Арифметика/преобразования/сравнения HUGE_VAL неизвестны, sizeof даёт8 в выбранном профиле; обычный вызов не выдуман.

### C

[`ClockScaleRuntimeTest#exactTypesAndRenderFormsUseIndependentBoundaryOracles`](../src/test/java/com/kniazkov/cpg/analysis/ClockScaleRuntimeTest.java): Независимые случаи целого типа/значения/записи, включая суффиксы и представимость; clock_t отделён от типа выражения макроса.

### WE

[`WeofRuntimeTest#exactTypesAndRenderFormsUseIndependentBoundaryOracles`](../src/test/java/com/kniazkov/cpg/analysis/WeofRuntimeTest.java): Минимумы signed и максимумы unsigned имеют точные записи/типы; проверены #if и unsigned-wrap; явный диапазон символов остаётся ограниченным.

### O

[`OffsetofRuntimeTest#structuredOperandsHaveExactSizeTypeBindingsAndTrueIntegralConstantContexts`](../src/test/java/com/kniazkov/cpg/analysis/OffsetofRuntimeTest.java): Структурированные type/designator, точный выбранный size_t и ICE-контексты; ограниченная раскладка, не все POD.

### V

[`VarargsRuntimeTest#boundOperandsTypedResultsAndUnknownVaListLayoutAcrossHeadersAndCopies`](../src/test/java/com/kniazkov/cpg/analysis/VarargsRuntimeTest.java): Связанные list/last/type и типизированные результаты в алиасах/копиях; раскладка va_list и runtime-значение неизвестны.

### J

[`SetjmpRuntimeTest#c90PermittedControllingAndDiscardedContextsRemainUnknown`](../src/test/java/com/kniazkov/cpg/analysis/SetjmpRuntimeTest.java): Допустимые исходные C90 controlling/discarded-контексты сохраняют неизвестный повторный возврат без фиктивной константы.

## Карта native/API/CLI методов

- **A**: [`AssertionRuntimeIT#successfulAssertionsResolveCopyEditAndRunThroughCliAndMaterializedExpansion`](../src/test/java/com/kniazkov/cpg/e2e/AssertionRuntimeIT.java).
- **E**: [`ErrnoRuntimeIT#sharedRuntimeStorageSurvivesApiCliCopyMaterializationAndRhsEditing`](../src/test/java/com/kniazkov/cpg/e2e/ErrnoRuntimeIT.java).
- **M**: [`MbCurMaxRuntimeIT#liveLocaleQuerySurvivesApiCliCopyMaterializationAndStringEditing`](../src/test/java/com/kniazkov/cpg/e2e/MbCurMaxRuntimeIT.java).
- **S**: [`StandardStreamsRuntimeIT#liveStreamsSurviveApiCliCopyMaterializationAndPayloadEditing`](../src/test/java/com/kniazkov/cpg/e2e/StandardStreamsRuntimeIT.java).
- **D**: [`SignalDispositionsRuntimeIT#nativeDispositionsSurviveApiCliCopyEditsReparsingAndTransforms`](../src/test/java/com/kniazkov/cpg/e2e/SignalDispositionsRuntimeIT.java).
- **H**: [`HugeValRuntimeIT#nativeInfinitySurvivesApiCliCopyEditsReparsingAndTransforms`](../src/test/java/com/kniazkov/cpg/e2e/HugeValRuntimeIT.java).
- **C**: [`ClockScaleRuntimeIT#independentTypesAndAllPositiveSuffixBoundariesUseActualCpgConstants`](../src/test/java/com/kniazkov/cpg/e2e/ClockScaleRuntimeIT.java).
- **WE**: [`WeofRuntimeIT#independentSignedAndUnsignedConstantsUseActualCpgReplacementsWithoutNativeAbiClaims`](../src/test/java/com/kniazkov/cpg/e2e/WeofRuntimeIT.java).
- **R**: [`RuntimeIntegerMacrosIT#actualTargetAndIndependentSyntheticFactsThroughApiCliCopyEditsAndNativeOracles`](../src/test/java/com/kniazkov/cpg/e2e/RuntimeIntegerMacrosIT.java).
- **O**: [`OffsetofRuntimeIT#realBuiltinSurvivesApiCliCopyEditsReparseGreenAndFolded`](../src/test/java/com/kniazkov/cpg/e2e/OffsetofRuntimeIT.java).
- **V**: [`VarargsRuntimeIT#apiCliCopiesTransformsEditsAndMaterializedMacrosMatchBothCompilers`](../src/test/java/com/kniazkov/cpg/e2e/VarargsRuntimeIT.java).
- **J**: [`SetjmpRuntimeIT#apiCliNativeReplaysAndRealEditsExecuteReturnsTwiceAtOptimization`](../src/test/java/com/kniazkov/cpg/e2e/SetjmpRuntimeIT.java).

Методы проверяют ограниченные original/generated/materialized сценарии GCC/Clang; наличие не равно свежему PASS. Новые numeric materialization LibraryMacrosIT и durable assert/limits artifacts прошли описанный ниже focused-прогон; он не запускал заново все перечисленные исторические семейства. Синтетические целые значения не доказывают native ILP32 object layout или числа libc хоста.

## Новые проверки приёмки: focused PASS

**ALL54**: [`StandardLibraryMacrosTest#allIntegerAndFloatingDefinitionsMatchIndependentTwoProfileFacts`](../src/test/java/com/kniazkov/cpg/parser/StandardLibraryMacrosTest.java)
проверяет все 16 целых и 29 float имён, WCHAR_MIN/MAX и семь NULL-обязательств
против независимых фактов двух профилей. Целые подстановки сравниваются точно;
float MAX/EPSILON/MIN с exact-binary BigDecimal либо точным округлением до
36 значащих цифр HALF_EVEN; остальные float-факты с фиксированными целыми.
Это не native binary128 conformance. Каждая PD-строка ниже явно содержит ALL54
в дополнение к историческому ключу.

**NUM48**: [`LibraryMacrosIT#actualMaterializedIntegerLimitsHaveIndependentLp64AndIlp32NumericOracles`](../src/test/java/com/kniazkov/cpg/e2e/LibraryMacrosIT.java)
прошёл 48 native-исполнений с независимыми ожиданиями профилей. Проверены
выражения, не native ILP32 object layout. **ASSERT18**: существующие success/failure
методы AssertionRuntimeIT прошли 18 durable native-исполнений с count 1/1/2,
edited 5/5/6 и шестью abort-случаями. Focused не заменяет полный verify,
independent review или exact-head CI.

Адаптация исходного assert до DR107 и nonreturning CFG остаются отдельными
пробелами: успешный runtime их не закрывает; рамка библиотечных свидетельств #100 релевантна, но её body исключает реализацию
макросов и не назначает DR107 явно. #98 перечисляет другие конкретные DR,
не DR107; #99 посвящена языку/приложениям, не этому библиотечному вопросу.
Сохраняется неоднозначность источника в общей #48, без объявления нового
production-дефекта или заявления, что существующая задача уже решает вопрос. Прежний отказ небезопасной правки условия сохранён.

ALL54 выбирает default LP64/binary128 и числовой профиль long32, unsigned char, binary80, rounds=0, NULL=0L; второй сохраняет default pointer width и не является полным ILP32 layout-профилем. NUM48 отдельно задаёт pointer32/long32 для synthetic ILP32 numeric-expression маршрута.

## Все 102 обязательства

PD=profile_definition, TF=target_fact, RB=runtime_binding, CI=compiler_intrinsic. OPEN остаётся partial/open. Колонка assertions отличает проверку чисел от наличия. Локаторы точно взяты из inventory; это не заявление о свежем чтении всех перечисленных абзацев.

| # | Header / old C spelling | Macro | State | Evidence / assertion boundary | Original locators |
|---:|---|---|---|---|---|
| 1 | `cassert` / `assert.h` | `assert` | RB | A: bounded semantics / ограниченная семантика | cpp03:19.2:p2, cpp03:19.2:p3, cpp03:17.4.2.1:p2; C90 7.2, 7.2.1.1 |
| 2 | `cerrno` / `errno.h` | `EDOM` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:19.3:table26, cpp03:19.3:p2; C90 7.1.4 |
| 3 | `cerrno` / `errno.h` | `ERANGE` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:19.3:table26, cpp03:19.3:p2; C90 7.1.4 |
| 4 | `cerrno` / `errno.h` | `EILSEQ` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:19.3:p2, cpp03:17.4.1.2:p4; C90 Amendment1 7.1.4 |
| 5 | `cerrno` / `errno.h` | `errno` | RB | E: bounded semantics / ограниченная семантика | cpp03:19.3:table26, cpp03:19.3:p2; C90 7.1.4 |
| 6 | `climits` / `limits.h` | `CHAR_BIT` | PD | [ALL54 focused PASS][all54] + I: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 7 | `climits` / `limits.h` | `SCHAR_MIN` | PD | [ALL54 focused PASS][all54] + I: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 8 | `climits` / `limits.h` | `SCHAR_MAX` | PD | [ALL54 focused PASS][all54] + I: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 9 | `climits` / `limits.h` | `UCHAR_MAX` | PD | [ALL54 focused PASS][all54] + I: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 10 | `climits` / `limits.h` | `CHAR_MIN` | PD | [ALL54 focused PASS][all54] + I: exact token / точный токен | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 11 | `climits` / `limits.h` | `CHAR_MAX` | PD | [ALL54 focused PASS][all54] + I: exact token / точный токен | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 12 | `climits` / `limits.h` | `MB_LEN_MAX` | PD | [ALL54 focused PASS][all54] + I: exact token / точный токен | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 13 | `climits` / `limits.h` | `SHRT_MIN` | PD | [ALL54 focused PASS][all54] + I: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 14 | `climits` / `limits.h` | `SHRT_MAX` | PD | [ALL54 focused PASS][all54] + I: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 15 | `climits` / `limits.h` | `USHRT_MAX` | PD | [ALL54 focused PASS][all54] + I: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 16 | `climits` / `limits.h` | `INT_MIN` | PD | [ALL54 focused PASS][all54] + I: predicate / условие | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 17 | `climits` / `limits.h` | `INT_MAX` | PD | [ALL54 focused PASS][all54] + I: exact token / точный токен | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 18 | `climits` / `limits.h` | `UINT_MAX` | PD | [ALL54 focused PASS][all54] + I: predicate / условие | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 19 | `climits` / `limits.h` | `LONG_MIN` | PD | [ALL54 focused PASS][all54] + I: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 20 | `climits` / `limits.h` | `LONG_MAX` | PD | [ALL54 focused PASS][all54] + I: exact token / точный токен | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 21 | `climits` / `limits.h` | `ULONG_MAX` | PD | [ALL54 focused PASS][all54] + I: exact token / точный токен | cpp03:18.2.2:table16, cpp03:18.2.2:p2; C90 5.2.4.2.1, 7.1.3 |
| 22 | `cfloat` / `float.h` | `FLT_RADIX` | PD | [ALL54 focused PASS][all54] + F: exact token/predicate / точный токен/условие | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 23 | `cfloat` / `float.h` | `FLT_ROUNDS` | PD | [ALL54 focused PASS][all54] + F: exact token/predicate / точный токен/условие | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 24 | `cfloat` / `float.h` | `FLT_MANT_DIG` | PD | [ALL54 focused PASS][all54] + F: exact token/predicate / точный токен/условие | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 25 | `cfloat` / `float.h` | `FLT_DIG` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 26 | `cfloat` / `float.h` | `FLT_MIN_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 27 | `cfloat` / `float.h` | `FLT_MIN_10_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 28 | `cfloat` / `float.h` | `FLT_MAX_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 29 | `cfloat` / `float.h` | `FLT_MAX_10_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 30 | `cfloat` / `float.h` | `FLT_MAX` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 31 | `cfloat` / `float.h` | `FLT_EPSILON` | PD | [ALL54 focused PASS][all54] + F: exact token/predicate / точный токен/условие | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 32 | `cfloat` / `float.h` | `FLT_MIN` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 33 | `cfloat` / `float.h` | `DBL_MANT_DIG` | PD | [ALL54 focused PASS][all54] + F: exact token/predicate / точный токен/условие | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 34 | `cfloat` / `float.h` | `DBL_DIG` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 35 | `cfloat` / `float.h` | `DBL_MIN_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 36 | `cfloat` / `float.h` | `DBL_MIN_10_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 37 | `cfloat` / `float.h` | `DBL_MAX_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 38 | `cfloat` / `float.h` | `DBL_MAX_10_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 39 | `cfloat` / `float.h` | `DBL_MAX` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 40 | `cfloat` / `float.h` | `DBL_EPSILON` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 41 | `cfloat` / `float.h` | `DBL_MIN` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 42 | `cfloat` / `float.h` | `LDBL_MANT_DIG` | PD | [ALL54 focused PASS][all54] + F: exact token/predicate / точный токен/условие | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 43 | `cfloat` / `float.h` | `LDBL_DIG` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 44 | `cfloat` / `float.h` | `LDBL_MIN_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 45 | `cfloat` / `float.h` | `LDBL_MIN_10_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 46 | `cfloat` / `float.h` | `LDBL_MAX_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 47 | `cfloat` / `float.h` | `LDBL_MAX_10_EXP` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 48 | `cfloat` / `float.h` | `LDBL_MAX` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 49 | `cfloat` / `float.h` | `LDBL_EPSILON` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 50 | `cfloat` / `float.h` | `LDBL_MIN` | PD | [ALL54 focused PASS][all54] + F: historical presence-only; ALL54 now checks values / ранее только наличие; ALL54 проверяет значения | cpp03:18.2.2:table17, cpp03:18.2.2:p4; C90 5.2.4.2.2, 7.1.3 |
| 51 | `clocale` / `locale.h` | `LC_ALL` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:22.3:table62, cpp03:22.3:p2; C90 7.4 |
| 52 | `clocale` / `locale.h` | `LC_COLLATE` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:22.3:table62, cpp03:22.3:p2; C90 7.4 |
| 53 | `clocale` / `locale.h` | `LC_CTYPE` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:22.3:table62, cpp03:22.3:p2; C90 7.4 |
| 54 | `clocale` / `locale.h` | `LC_MONETARY` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:22.3:table62, cpp03:22.3:p2; C90 7.4 |
| 55 | `clocale` / `locale.h` | `LC_NUMERIC` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:22.3:table62, cpp03:22.3:p2; C90 7.4 |
| 56 | `clocale` / `locale.h` | `LC_TIME` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:22.3:table62, cpp03:22.3:p2; C90 7.4 |
| 57 | `cmath` / `math.h` | `HUGE_VAL` | CI | H: bounded semantics / ограниченная семантика | cpp03:26.5:p1, cpp03:26.5:p2; C90 7.5 |
| 58 | `csetjmp` / `setjmp.h` | `setjmp` | OPEN | J: partial; residual SETJMP | cpp03:18.7:table20, cpp03:18.7:p2, cpp03:18.7:p4; C90 7.6, 7.6.1.1 |
| 59 | `csignal` / `signal.h` | `SIGABRT` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:18.7:table22, cpp03:18.7:p2; C90 7.7 |
| 60 | `csignal` / `signal.h` | `SIGFPE` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:18.7:table22, cpp03:18.7:p2; C90 7.7 |
| 61 | `csignal` / `signal.h` | `SIGILL` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:18.7:table22, cpp03:18.7:p2; C90 7.7 |
| 62 | `csignal` / `signal.h` | `SIGINT` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:18.7:table22, cpp03:18.7:p2; C90 7.7 |
| 63 | `csignal` / `signal.h` | `SIGSEGV` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:18.7:table22, cpp03:18.7:p2; C90 7.7 |
| 64 | `csignal` / `signal.h` | `SIGTERM` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:18.7:table22, cpp03:18.7:p2; C90 7.7 |
| 65 | `csignal` / `signal.h` | `SIG_DFL` | RB | D: bounded semantics / ограниченная семантика | cpp03:18.7:table22, cpp03:18.7:p2; C90 7.7 |
| 66 | `csignal` / `signal.h` | `SIG_ERR` | RB | D: bounded semantics / ограниченная семантика | cpp03:18.7:table22, cpp03:18.7:p2; C90 7.7 |
| 67 | `csignal` / `signal.h` | `SIG_IGN` | RB | D: bounded semantics / ограниченная семантика | cpp03:18.7:table22, cpp03:18.7:p2; C90 7.7 |
| 68 | `cstdarg` / `stdarg.h` | `va_start` | OPEN | V: partial; residual VARARGS | cpp03:18.7:table19, cpp03:18.7:p2, cpp03:18.7:p3; C90 7.8, 7.8.1 |
| 69 | `cstdarg` / `stdarg.h` | `va_arg` | OPEN | V: partial; residual VARARGS | cpp03:18.7:table19, cpp03:18.7:p2, cpp03:18.7:p3; C90 7.8, 7.8.1 |
| 70 | `cstdarg` / `stdarg.h` | `va_end` | OPEN | V: partial; residual VARARGS | cpp03:18.7:table19, cpp03:18.7:p2, cpp03:18.7:p3; C90 7.8, 7.8.1 |
| 71 | `cstddef` / `stddef.h` | `offsetof` | OPEN | O: partial; residual OFFSET | cpp03:18.1:table15, cpp03:18.1:p3, cpp03:18.1:p5; C90 7.1.6 |
| 72 | `cstdio` / `stdio.h` | `_IOFBF` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 73 | `cstdio` / `stdio.h` | `_IOLBF` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 74 | `cstdio` / `stdio.h` | `_IONBF` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 75 | `cstdio` / `stdio.h` | `BUFSIZ` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 76 | `cstdio` / `stdio.h` | `EOF` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 77 | `cstdio` / `stdio.h` | `FOPEN_MAX` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 78 | `cstdio` / `stdio.h` | `FILENAME_MAX` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 79 | `cstdio` / `stdio.h` | `L_tmpnam` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 80 | `cstdio` / `stdio.h` | `SEEK_CUR` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 81 | `cstdio` / `stdio.h` | `SEEK_END` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 82 | `cstdio` / `stdio.h` | `SEEK_SET` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 83 | `cstdio` / `stdio.h` | `TMP_MAX` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 84 | `cstdio` / `stdio.h` | `stdin` | RB | S: bounded semantics / ограниченная семантика | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 85 | `cstdio` / `stdio.h` | `stdout` | RB | S: bounded semantics / ограниченная семантика | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 86 | `cstdio` / `stdio.h` | `stderr` | RB | S: bounded semantics / ограниченная семантика | cpp03:27.8.2:table94, cpp03:27.8.2:p1; C90 7.9.1 |
| 87 | `cstdlib` / `stdlib.h` | `EXIT_FAILURE` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:18.3:table18, cpp03:18.3:p2; C90 7.10 |
| 88 | `cstdlib` / `stdlib.h` | `EXIT_SUCCESS` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:18.3:table18, cpp03:18.3:p2; C90 7.10 |
| 89 | `cstdlib` / `stdlib.h` | `RAND_MAX` | TF | R + RV: selected fact + schema boundary / выбранный факт + границы схемы | cpp03:26.5:p1, cpp03:26.5:p2; C90 7.10, 7.10.2.1 |
| 90 | `cstdlib` / `stdlib.h` | `MB_CUR_MAX` | RB | M: bounded semantics / ограниченная семантика | cpp03:21.4:table49, cpp03:21.4:p2; C90 7.10 |
| 91 | `ctime` / `time.h` | `CLOCKS_PER_SEC` | TF | C: bounded semantics / ограниченная семантика | cpp03:18.7:table21, cpp03:18.7:p2; C90 7.12.1 |
| 92 | `cwchar` / `wchar.h` | `WCHAR_MIN` | PD | [ALL54 focused PASS][all54] + W: exact promoted tokens / точные продвинутые токены | cpp03:21.4:table48, cpp03:21.4:p2; C90 Amendment1 7.16.1 |
| 93 | `cwchar` / `wchar.h` | `WCHAR_MAX` | PD | [ALL54 focused PASS][all54] + W: exact promoted tokens / точные продвинутые токены | cpp03:21.4:table48, cpp03:21.4:p2; C90 Amendment1 7.16.1 |
| 94 | `cwchar` / `wchar.h` | `WEOF` | TF | WE: bounded semantics / ограниченная семантика | cpp03:21.4:table48, cpp03:21.4:p2; C90 Amendment1 7.16.1 |
| 95 | `cwctype` / `wctype.h` | `WEOF` | TF | WE: bounded semantics / ограниченная семантика | cpp03:21.4:table46, cpp03:21.4:p2; C90 Amendment1 7.15.1 |
| 96 | `cstddef` / `stddef.h` | `NULL` | PD | [ALL54 focused PASS][all54] + N: selected0L / выбранный0L | cpp03:18.1:table15, cpp03:18.1:p4; C90 7.1.6 |
| 97 | `cstdio` / `stdio.h` | `NULL` | PD | [ALL54 focused PASS][all54] + N: selected0L / выбранный0L | cpp03:27.8.2:table94, cpp03:18.1:p4; C90 7.9.1 |
| 98 | `cstdlib` / `stdlib.h` | `NULL` | PD | [ALL54 focused PASS][all54] + N: selected0L / выбранный0L | cpp03:18.3:p2, cpp03:18.1:p4; C90 7.10 |
| 99 | `cstring` / `string.h` | `NULL` | PD | [ALL54 focused PASS][all54] + N: selected0L / выбранный0L | cpp03:21.4:table47, cpp03:18.1:p4; C90 7.11.1 |
| 100 | `ctime` / `time.h` | `NULL` | PD | [ALL54 focused PASS][all54] + N: selected0L / выбранный0L | cpp03:20.5:p1, cpp03:18.1:p4; C90 7.12.1 |
| 101 | `clocale` / `locale.h` | `NULL` | PD | [ALL54 focused PASS][all54] + N: selected0L / выбранный0L | cpp03:22.3:table62, cpp03:18.1:p4; C90 7.4 |
| 102 | `cwchar` / `wchar.h` | `NULL` | PD | [ALL54 focused PASS][all54] + N: selected0L / выбранный0L | cpp03:21.4:table48, cpp03:18.1:p4; C90 Amendment1 7.16.1 |

## Точность assert: контракт и границы анализа

Исходный C90 §7.2.1.1, printed101/PDF111, задаёт synopsis `assert(int expression)`
и сравнение с нулём. Исходный C++03 §19.2/3, printed358/PDF386, наследует
заголовок; §4.12/1, printed63/PDF91, преобразует целый ноль в false, а любое
ненулевое целое в true. Поэтому выбранный `static_cast<bool>(expression)`
сохраняет обязательную истинность проверяемых целых операндов, включая
`assert(++count)`, без молчаливого применения DR107. Более широкий C++-домен
операндов остаётся вопросом исходного текста/адаптации, не воспроизведённым
нарушением обязательного контракта.

Nonreturning CFG является известным ограничением консервативной точности,
не свидетельством неверного native-завершения. Реальный выбранный `::__assert_fail`
вызывает abort. `AssertionRuntimeIT#failureDiagnosesRawExpressionPresumedFileLineAndTerminates`
проверяет ненулевое завершение, raw `ZERO`, presumed `assertion-input.cpp:73`
и отсутствие `AFTER_ASSERT`, destructor `UNWOUND` и `ATEXIT`. CPG не выводит
терминальный CFG-факт из helper; точное доказательство abort-flow не заявлено.

Правка только условия в графе явно отклоняется, если требуется повторная
строкификация. Success/failure integration-методы проверяют отказ генерации,
предотвращающий устаревший текст диагностики. Правка исходного аргумента с
повторным разбором и несвязанные правки графа поддерживаются. Это объявленная
граница редактора, не скрытая неверная генерация и не заявленный production-дефект.

## Остатки и целостность источников

- **OFFSET — [#145](https://github.com/kniazkov/code-property-graph/issues/145):** реальные ограничения выбранной раскладки/type/designator, не требование всех ABI. V1/v2/v3 и шаблонные prerequisites остаются ограниченными; #94/#104 не дублируются.
- **VARARGS — [#146](https://github.com/kniazkov/code-property-graph/issues/146):** единое семейство va_start/va_arg/va_end: локальное хранение, формы извлечения, forwarding/lifecycle; не универсальное доказательство count/UB.
- **SETJMP — [#147](https://github.com/kniazkov/code-property-graph/issues/147):** direct-local buffer, escaping/nonlocal формы и консервативный nonlocal flow; не задача обычных exception specifications #96.
- #100 явно исключает реализацию макросов; #99 относится к языковым доказательствам; #97 к конкретным IFNDR/UB-эталонам. Они не заменяют эти остаточные семейства. #48 открыта.

SHA256 исходного пользовательского ISO/IEC14882:2003:
`6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a`.
SHA256 оригинального C90:
`26c6a994c7e4757ab1e87cad871d88c63b4ed60f7bfd9df773f6afc329db5db7`.
Оригиналы теперь доступны, в отличие от исторических отчётов об отсутствии PDF;
это не превращает прежние частичные свидетельства в полное чтение источников.
В недавних пакетах проверены §18.1/3,5 (printed333–334/PDF361–362),
§9.6/1–3 (printed163/PDF191) и C90 §7.1.6 (printed98/PDF108).
В этом пакете дополнительно перечитаны оригинальные §18.2.2 tables16/17,
printed341/PDF369; §19.2/2–3, printed358/PDF386; C90 §7.2/7.2.1.1,
printed101/PDF111. Прочие локаторы сохраняют основу source-reviewed ledger. EILSEQ/WEOF из Amendment1
нельзя исключать по отсутствию в C90; полное повторное чтение Amendment1 не заявлено.
Границы publisher preview описаны в отчётах WEOF/runtime-integer. Закрытый исходный
текст, hashes и classifications не переписываются.

## Статус исполнения

Focused-команда (вторая попытка PASS; ошибка harness первой описана ниже):

```sh
mvn -Dtest=StandardLibraryMacrosTest,AssertionMacrosTest -Dit.test=LibraryMacrosIT,AssertionRuntimeIT verify
```

В 15 focused unit-тестов входит новый ALL54. Вторая focused-попытка прошла
**15 unit-тестов и шесть integration-тестов** за **36.640 секунды**, без
failures/errors/skips.

Затем полный clean verify **ПРОШЁЛ: 1 645 unit-тестов и 167 integration-тестов**,
без failures/errors/skips, за **54 минуты 34 секунды**:

```sh
mvn -B -ntp -s "$CPG_MAVEN_SETTINGS" clean verify
```

SHA-256 свежего локального JAR:
`47a1483bea93d8042e1f3d0de95fbe5ce02273b321266bc27e57495dc44eb196`.
Независимое review одобрило source tree `102e95d25a3842bb52f6264235be8b0cdbce897d`. Проверки каталога GCC и Clang, corpus-report checker (42 сценария плюс два harness checks), строгий private JavaDoc и полный demo также прошли. Exact-head обязательный CI и post-merge master остаются pending; локальные результаты эти гейты не заменяют.

Первая focused-попытка завершилась ошибкой за **32.207 секунды**: новый
harness требовал16 раскрытий и в преобразованном исходнике, где безопасная правка
может заменить часть макросов. Проверка16 теперь относится только к original,
а metadata сохраняет фактические имена раскрытий. Это исправление test-harness,
не production-кода. Исправленная вторая попытка прошла, как указано выше; первая остаётся
ошибкой harness, не production-fix и не full PASS.

Аудит артефактов: `target/library-macros-oracle/` содержит 48 записей,
по 24 на компилятор, и 240 проверенных referenced hashes. 32 materialized-маршрута,
включая 16 synthetic-profile materialized; восемь original-input proof-случаев
сохраняют требование ровно 16 раскрытий. Преобразованные маршруты записывают
фактически оставшиеся имена вместо устаревшего счётчика.
`target/assertion-runtime-oracle/` содержит 18 записей, по девять на компилятор,
и 36 проверенных referenced hashes: восемь count 1/1/2, четыре edited 5/5/6,
шесть abort. Независимо проверены и сохранённые focused-артефакты, и заново полученные артефакты полного набора: по 66 записей и 276 referenced hashes. Число native-записей отдельно от 1645 unit и 167 integration tests.

[all54]: ../src/test/java/com/kniazkov/cpg/parser/StandardLibraryMacrosTest.java

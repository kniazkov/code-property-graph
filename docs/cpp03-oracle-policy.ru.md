# Эталоны корректных программ и защитная политика CPG (#97)

Исправляется доказательная база, а не семантика парсера. Основание — уже
проверенные по печатной ISO/IEC 14882:2003 записи реестра глав 11–16.
Позднейшие стандарты и поведение современного компилятора не меняют категорию
случая в выбранной редакции. Нормативные поля, хеши, категории диагностики и
статусы покрытия реестра сохранены; изменены только `tests` и `gap` пяти записей.

| Случай | Что проверяется |
| --- | --- |
| Полная специализация без явного инстанцирования | `fullSpecializationSelectsItsDefinitionWithoutExplicitInstantiation`: один экземпляр/запрос, цель вызова и тело специализации, отсутствие ошибок; копия и повторное разрешение. |
| Явное инстанцирование без полной специализации | `explicitInstantiationSelectsPrimaryWithoutFullSpecialization`: один экземпляр первичного шаблона, два запроса (объявление и вызов), возвращаемый параметр и цель вызова; копия и повторное разрешение. |
| Оба объявления для одних аргументов, §14.7/5 | IFNDR. `ifndrSpecializationAndInstantiationOnlyChecksAnalysisRobustness` проверяет только отсутствие аварии анализа, сериализации, копирования и повторного разрешения. Нет требования принять/отклонить программу, выбрать определённое тело, число экземпляров или результат исполнения. |
| Пустой аргумент, §16.3/10 | UB; `ubPolicyRejectsEmptyMacroArguments` закрепляет отказ CPG. Вызов макроса без параметров и пустое раскрытие object-like макроса остаются отдельными корректными случаями. |
| Некорректный токен после `##`, §16.3.3/3 | UB; `ubPolicyRejectsInvalidPasteResults` закрепляет отказ CPG. Корректная склейка/повторное сканирование проверяются отдельно. |
| `#line 0` / `#line 32768`, §16.4/3 | UB; `ubPolicyRejectsOutOfRangeLineNumbers` закрепляет отказ CPG; допустимые координаты проверяются отдельно. |
| Изменение предопределённых имён, §16.8/3 | UB; `ubPolicyRejectsPredefinedNameChanges` закрепляет отказ CPG. |

Тесты шаблонов находятся в `TemplateInstantiationSemanticsTest`, препроцессора —
в `CppPreprocessorTest`. Прежний `fullSpecializationAndExplicitInstantiation`
заменён тремя тестами. Из `rejectsMalformedAndNonCpp03MacroForms`,
`pasteUsesRawArgumentsThenRescansAndNeverPastesAnArgumentHashHash` и
`lineControlAndBuiltinsKeepLogicalCoordinatesSeparateFromPhysicalRanges`
выделены четыре UB-политики. Защитные assertions не удалены.

## Границы эталона

Обязательная диагностика корректно классифицированной ошибки, добровольный
отказ на UB и IFNDR — разные требования. Отсутствие обязательной диагностики
не запрещает анализатору сообщать об опасном входе. Неуточнённый порядок `##`
не задаёт единственного допустимого порядка в ISO: тест происхождения
`stringizeAndChainedPasteKeepAllPhysicalParticipants` фиксирует трассу CPG,
а выбранный пример `pre ## fix ## ed` имеет одинаковый итог при обоих порядках.
Implementation-defined свойства проверяются для указанного профиля; например,
отсутствие `__STDC__` относится к текущему профилю CPG, а не ко всем реализациям.

`Cpp03UndefinedBehaviorPolicyIT` использует четыре сохраняемых исходника из
[src/test/resources/cpp03-ub-policy](../src/test/resources/cpp03-ub-policy/README.md).
Он проверяет API/CLI, диагностический JSON, сохранение ошибки при копировании и
переразрешении и запрет генерации исходников. Входы с UB не компилируются и не
исполняются как нативные эталоны. IFNDR-вход также не запускается нативно.

Для корректных случаев используется существующий сценарий
`cpp03/template-instantiation`: `specialized<int>` имеет полную специализацию,
а отдельный `identity<int>` явно инстанцирован. Сценарий проверяет граф,
API/CLI/JSON, копирование, повторный разбор и native original/generated/folded
под GCC и Clang с `-std=c++03 -pedantic-errors`.

## Воспроизведение и приёмка

```sh
mvn -B -ntp -Dtest=TemplateInstantiationSemanticsTest,CppPreprocessorTest \
  -Dit.test=Cpp03UndefinedBehaviorPolicyIT,Cpp03CorpusIT \
  -Dcpg.cpp03.scenario=template-instantiation verify
python3 scripts/test-cpp03-paragraphs.py
python3 scripts/check-cpp03-paragraphs.py --require-source-complete
mvn -B -ntp clean verify
```

JDK 21, Maven, GCC, Clang и Graphviz обязательны. Полный `verify` и GitHub CI
на финальном коммите — отдельные гейты; результаты, SHA ревизии и SHA256 нового
JAR фиксируются в PR после исполнения, а не выводятся из наличия тестов.
Полнота §14.7/5 и остальных параграфов этим пакетом не заявляется; #48 открыта.

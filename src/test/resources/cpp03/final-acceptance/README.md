<!-- Integrated multifile C++03 acceptance. MIT License. -->
# Final integrated acceptance / Итоговая сквозная проверка (#48)

## English

This is one executable program, not separate demonstrations:

- `include/pipeline.h` is included by both translation units. It defines `FINAL_TWICE`, `FINAL_ERROR`, the function template `twice<T>`, the abstract `Evaluator`, and `Doubler`.
- `main.cpp` appends 3 and 4 to `std::vector<int>` and calls the separately compiled `evaluate.cpp` through its header declaration.
- `evaluate` reads the vector, dispatches `Evaluator::apply` virtually to `Doubler::apply`, and that implementation calls the concrete `twice<int>` instance. Its addition comes from the header macro.
- After the first result, `main` writes -2 through the vector's subscript. The second cross-file call takes the negative branch and throws the macro-supplied integer 3. The caller catches it and prints the first result, caught value, and container size.

Every original/generated/reparsed executable must print exactly `10 3 2\n`, exit 0, and leave stderr empty. Both GCC and Clang compile all three trees together with `-std=c++03 -pedantic-errors`.

The `final-acceptance` corpus entry calls `FinalAcceptance`. It checks API and independently parsed JSON IDs for cross-unit declarations and call targets; concrete template identity and both instantiation causes; virtual static/possible targets and override identity; macro definition/invocation/token ancestry; real vector operation targets, input edges, mutated objects and the subsequent element-write dependency; call-site-specific argument flow; and the throw/exception/catch edges. It compares the entire API/CLI snapshot, the generated file set and contents, then runs the packaged CLI again on copied generated sources and repeats the checks. The second generation must be identical to the first.

No unspecified evaluation order affects the output. Macro arguments have no side effects, all integer arithmetic is small and representable, both vector indices are in bounds, no iterator or element reference survives reallocation, and the virtual destructor is defined. No pointer addresses, ABI sizes, allocation counts, locale-sensitive conversions, or vendor exception text are used as an oracle. This is evidence for this composed scenario, not a claim that every C++03 rule or library facility is implemented.

## Русский

Это одна исполняемая программа, а не набор независимых демонстраций:

- Общий `include/pipeline.h` подключён обеими единицами трансляции. В нём объявлены макросы `FINAL_TWICE` и `FINAL_ERROR`, шаблон функции `twice<T>`, абстрактный `Evaluator` и производный `Doubler`.
- `main.cpp` добавляет 3 и 4 в `std::vector<int>` и вызывает функцию из отдельно компилируемого `evaluate.cpp` через объявление в заголовке.
- `evaluate` читает vector и виртуально вызывает `Evaluator::apply`. Реализация `Doubler::apply` вызывает конкретный экземпляр `twice<int>`, а его сложение происходит из макроса заголовка.
- Затем `main` записывает -2 через индекс vector. Второй межфайловый вызов попадает в отрицательную ветвь и бросает целое 3, полученное из макроса. Вызывающая функция перехватывает его и печатает первый результат, значение исключения и размер контейнера.

Исходная программа, результат генерации и результат повторного CLI-разбора/генерации обязаны вывести ровно `10 3 2\n`, завершиться с кодом 0 и пустым stderr. Все три дерева собираются обоими компиляторами GCC и Clang с `-std=c++03 -pedantic-errors`.

Сценарий корпуса `final-acceptance` использует `FinalAcceptance`. В API и независимо прочитанном JSON проверяются реальные цели межфайловых вызовов и объявлений, единый конкретный экземпляр шаблона и обе причины инстанцирования, статическая и возможная виртуальная цель, override-связь, определения/вызовы/токены макросов, цели операций vector, входные зависимости и изменяемые объекты, зависимость последующего чтения от записи элемента, межпроцедурные связи параметров с конкретным вызовом, рёбра throw/exception/catch. Полностью сравниваются снимки API/CLI, набор и содержимое выходных файлов. CLI повторно читает копию сгенерированных файлов; проверки повторяются, а второй результат генерации должен совпасть с первым.

Результат не зависит от неуточнённого порядка вычислений. Аргументы макроса не имеют побочных эффектов, арифметика мала и представима, оба индекса допустимы, итераторы и ссылки на элементы не переживают перераспределение памяти, виртуальный деструктор определён. Адреса, размеры ABI, число выделений памяти, локаль и текст стандартных исключений не используются как эталон. Проверяется именно этот составной сценарий, а не полнота поддержки всех правил и средств библиотеки C++03.

## Run / Запуск

From the repository root / Из корня репозитория:

```sh
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=final-acceptance verify
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --stage green --cpp-standard c++03 \
  --input src/test/resources/cpp03/final-acceptance/sources \
  --json target/final-acceptance.json --out target/final-acceptance
for compiler in g++ clang++; do
  "$compiler" -std=c++03 -pedantic-errors target/final-acceptance/main.cpp \
    target/final-acceptance/evaluate.cpp -o "target/final-acceptance-$compiler"
  "target/final-acceptance-$compiler"
done
```

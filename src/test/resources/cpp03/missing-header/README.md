# missing-header

Negative include-chain acceptance. main.cpp includes outer.h, which includes inner.h, which requests missing.h. The strict API throws at inner.h:1:2 with the whole chain. Since #21, the recovering API/CLI retains the failure in a diagnostic graph; the CLI exits 1, writes requested JSON/SVG, and creates no source output.

Отрицательный пример цепочки включений main.cpp → outer.h → inner.h → missing.h. Строгий API выбрасывает исключение с inner.h:1:2 и всей цепочкой. С #21 восстанавливающий API/CLI сохраняет ошибку в диагностическом графе: CLI возвращает 1, записывает запрошенные JSON/SVG, но не создаёт исходников.

```sh
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=missing-header verify
```

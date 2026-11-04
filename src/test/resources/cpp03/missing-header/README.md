# missing-header

Negative include-chain acceptance. main.cpp includes outer.h, which includes inner.h, which requests missing.h. CLI/API must diagnose inner.h:1:2, include the whole chain, and create no exports.

Отрицательный пример цепочки включений main.cpp → outer.h → inner.h → missing.h. API/CLI должны сообщить inner.h:1:2, всю цепочку и не создавать экспортов.

```sh
mvn -Dit.test=Cpp03CorpusIT -Dcpg.cpp03.scenario=missing-header verify
```

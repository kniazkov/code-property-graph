# Воспроизводимый пример раскладки v7

`main.cpp` — исходник из e2e-теста `PaddingBitFieldLayoutIT`, `profile.json` —
явный профиль `x86_64-sysv-padding-v7`. Запуск из корня проекта после сборки:

```sh
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --input src/test/resources/padding-bitfield --cpp-standard c++03 --platform-profile src/test/resources/padding-bitfield/profile.json --stage folded --json target/padding-v7.json
mvn -Dtest=PaddingBitFieldLayoutTest -Dit.test=PaddingBitFieldLayoutIT verify
```

Второй запуск требует GCC и Clang на x86-64 Linux с LP64. Тест проверяет
числовые факты через отдельную обёртку; самостоятельный запуск `main.cpp`
лишь проверяет компиляцию/выполнение исходника и не заменяет assertions.
Артефакты: `target/padding-bitfield-oracle/`.

[Контракт, таблица ожидаемых значений и ограничения](../../../../docs/cpp03-padding-bit-field-layout-verification.md).

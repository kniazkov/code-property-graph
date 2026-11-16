# Воспроизводимый пример раскладки v9

`main.cpp` — исходник из e2e-теста `WideBitFieldLayoutIT`, `profile.json` —
явный профиль `x86_64-sysv-wide-v9`. Запуск из корня проекта после сборки:

```sh
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --input src/test/resources/wide-bitfield --cpp-standard c++03 --platform-profile src/test/resources/wide-bitfield/profile.json --stage folded --json target/padding-v9.json
mvn -Dtest=WideBitFieldLayoutTest -Dit.test=WideBitFieldLayoutIT verify
```

Второй запуск требует GCC и Clang на x86-64 Linux с LP64. Тест проверяет
числовые факты через отдельную обёртку; самостоятельный запуск `main.cpp`
лишь проверяет компиляцию/выполнение исходника и не заменяет assertions.
Артефакты: `target/wide-bitfield-oracle/`.

[Контракт, ожидаемые значения и ограничения](../../../../docs/cpp03-wide-bitfield-verification.md).

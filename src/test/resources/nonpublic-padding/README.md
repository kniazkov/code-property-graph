# Воспроизводимый пример раскладки v8

`main.cpp` — исходник из e2e-теста `NonpublicPaddingLayoutIT`, `profile.json` —
явный профиль `x86_64-sysv-access-v8`. Запуск из корня проекта после сборки:

```sh
java -jar target/cpg-0.0.1-SNAPSHOT.jar --from cpp --input src/test/resources/nonpublic-padding --cpp-standard c++03 --platform-profile src/test/resources/nonpublic-padding/profile.json --stage folded --json target/padding-v8.json
mvn -Dtest=NonpublicPaddingLayoutTest -Dit.test=NonpublicPaddingLayoutIT verify
```

Второй запуск требует GCC и Clang на x86-64 Linux с LP64. Тест проверяет
числовые факты через отдельную обёртку; самостоятельный запуск `main.cpp`
лишь проверяет компиляцию/выполнение исходника и не заменяет assertions.
Артефакты: `target/nonpublic-padding-oracle/`.

[Контракт, ожидаемые значения и ограничения](../../../../docs/cpp03-nonpublic-padding-verification.md).

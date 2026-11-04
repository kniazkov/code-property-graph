<!-- CPG: материалы для будущего исследования Ascend C. -->
# Ascend C — исходники Huawei для будущей проверки парсера

**Решение от 4 ноября 2026 года. Поддержку диалекта отложили.** Сначала расширяем базовый C++. Вернуться к этим примерам имеет смысл, когда сможем разобрать обычную C++-часть выбранного ядра и отдельно перечислить конструкции, для которых требуется поддержка Ascend C.

## Векторное сложение из Ascend/samples

В файле `add_custom.cpp` рядом находятся класс с реализацией векторного сложения, точка входа ядра и код его запуска. Это удобный исходник для сравнения: в пределах одного файла видны расширения объявления функции, типа указателя и выражения вызова. В заголовке указана Huawei Technologies, в корне репозитория размещена лицензия Apache-2.0. Ссылки привязаны к конкретной ревизии. [Исходник](https://github.com/Ascend/samples/blob/fc2aea93e48be8f8cee51d0c6c1b13a08155f83a/cplusplus/level1_single_api/4_op_dev/6_ascendc_custom_op/kernel_invocation/Add/add_custom.cpp) · [Лицензия](https://github.com/Ascend/samples/blob/fc2aea93e48be8f8cee51d0c6c1b13a08155f83a/LICENSE)

| Место | На что обратить внимание при разборе |
|---|---|
| [Объявление ядра, строка 101](https://github.com/Ascend/samples/blob/fc2aea93e48be8f8cee51d0c6c1b13a08155f83a/cplusplus/level1_single_api/4_op_dev/6_ascendc_custom_op/kernel_invocation/Add/add_custom.cpp#L101) | У функции `add_custom` стоят `extern "C"`, `__global__` и `__aicore__`. Первое — обычная спецификация языкового связывания C++. Для двух остальных предстоит определить, как сохранять спецификаторы в узле функции. |
| [Приведение указателя, строка 34](https://github.com/Ascend/samples/blob/fc2aea93e48be8f8cee51d0c6c1b13a08155f83a/cplusplus/level1_single_api/4_op_dev/6_ascendc_custom_op/kernel_invocation/Add/add_custom.cpp#L34) | В записи `(__gm__ half*)` квалификатор адресного пространства относится к типу внутри приведения. Сначала нужна поддержка самого приведения и типа указателя в базовом C++. |
| [Запуск ядра, строка 112](https://github.com/Ascend/samples/blob/fc2aea93e48be8f8cee51d0c6c1b13a08155f83a/cplusplus/level1_single_api/4_op_dev/6_ascendc_custom_op/kernel_invocation/Add/add_custom.cpp#L112) | В `add_custom<<<blockDim, l2ctrl, stream>>>(x, y, z)` первый список задаёт конфигурацию запуска, второй передаёт аргументы ядру. При моделировании важно сохранить роли и порядок обоих списков. Для лексера интересна граница между `<<<` и уже знакомым `<<`. |

В том же репозитории есть вариант сложения для вызова через ACL. В нём `__aicore__` встречается у конструктора и метода класса, а точка входа принимает дополнительные параметры `workspace` и `tiling`. Это второй пример для проверки правил объявления. В обоих образцах подключается `kernel_operator.h` и используется пространство имён `tik2`. [Конструктор и метод](https://github.com/Ascend/samples/blob/fc2aea93e48be8f8cee51d0c6c1b13a08155f83a/cplusplus/level1_single_api/4_op_dev/6_ascendc_custom_op/acl_invocation/op_dev/op_kernel/add_custom.cpp#L13-L17) · [Точка входа](https://github.com/Ascend/samples/blob/fc2aea93e48be8f8cee51d0c6c1b13a08155f83a/cplusplus/level1_single_api/4_op_dev/6_ascendc_custom_op/acl_invocation/op_dev/op_kernel/add_custom.cpp#L82)

## Ядро compressor из vLLM Ascend

В этом примере выбор заголовка зависит от проверки `__CCE_AICORE__` в директиве `#if`. Ниже находится функция `compressor` со спецификаторами `__global__ __aicore__`. Такой файл пригодится, когда понадобится проверить взаимодействие диалекта с условной компиляцией. В заголовке самого файла указаны Huawei Technologies и **CANN Open Software License Agreement Version 2.0**. [Заголовок и условная компиляция](https://github.com/vllm-project/vllm-ascend/blob/32f96ea9a663068705e9f3a87b2d549c95050a54/csrc/attention/compressor/op_kernel/compressor.cpp#L1-L17) · [Объявление ядра](https://github.com/vllm-project/vllm-ascend/blob/32f96ea9a663068705e9f3a87b2d549c95050a54/csrc/attention/compressor/op_kernel/compressor.cpp#L36)

## С чего продолжить базовый C++

Короткий ориентир — свободная функция `void twice(int value, int* result)` с присваиванием `*result = value * 2;` и вызовом `twice(x, &y);`. На ней можно проверить произвольное имя функции, список параметров, объявление указателя, взятие адреса, разыменование и связь вызова с объявлением. Дальше обычные C++-конструкции из примеров Huawei можно выделять в такие же самостоятельные проверки. Конкретную форму узлов для расширений Ascend C оставляем открытой.

#include <vector>
#include "api.h"
int main() {
    std::vector<int> values(3);
    Tagged tagged = { 6 };
    int value = 0;
    int array[3] = { 1, 2, 3 };
    Box<int> box = { 8 };
    return outer(values) + other() + choose(tagged) + choose(value)
        + classify(value) + classify(&value)
        + Select<double>::value() + Select<double*>::value()
        + Select<int>::value() + Select<const double*>::value() + specialized(0)
        + length(array) + readBox(box) + fixedValue<4>() - 60;
}

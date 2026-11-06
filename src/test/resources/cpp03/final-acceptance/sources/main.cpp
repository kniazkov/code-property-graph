#include "include/pipeline.h"
#include <cstdio>

int main() {
    std::vector<int> values;
    values.push_back(3);
    values.push_back(4);
    Doubler doubler;
    const Evaluator& evaluator = doubler;
    int accepted = evaluate(evaluator, values);
    values[0] = -2;
    int caught = 0;
    try {
        evaluate(evaluator, values);
    } catch (int error) {
        caught = error;
    }
    int count = static_cast<int>(values.size());
    std::printf("%d %d %d\n", accepted, caught, count);
    return accepted == 10 && caught == FINAL_ERROR && count == 2 ? 0 : 1;
}

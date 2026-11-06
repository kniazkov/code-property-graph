#include "include/pipeline.h"

int evaluate(const Evaluator& evaluator, const std::vector<int>& values) {
    if (values.at(0) < 0) {
        throw FINAL_ERROR;
    }
    return evaluator.apply(values[0]) + values[1];
}

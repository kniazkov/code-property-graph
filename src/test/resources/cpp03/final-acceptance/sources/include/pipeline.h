#ifndef FINAL_ACCEPTANCE_PIPELINE_H
#define FINAL_ACCEPTANCE_PIPELINE_H

#include <vector>

#define FINAL_TWICE(value) ((value) + (value))
#define FINAL_ERROR 3

template<class T> T twice(T value) {
    return FINAL_TWICE(value);
}

struct Evaluator {
    virtual int apply(int value) const = 0;
    virtual ~Evaluator() {}
};

struct Doubler : Evaluator {
    int apply(int value) const { return twice<int>(value); }
};

int evaluate(const Evaluator& evaluator, const std::vector<int>& values);

#endif

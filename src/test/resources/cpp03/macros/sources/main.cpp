#define STR_RAW(x) #x
#define STR(x) STR_RAW(x)
#define HEADER include/values.h
#include STR(HEADER)
#include "include/values.h"
#include <numbers.h>
#define CAT(a, b) a ## b
#define SQUARE(x) ((x) * (x))
#define ID(x) x
#define EMPTY(x)
#define FUN(x) ((x) + 1)
#define SELF SELF
#define A B
#define B A
int main() {
    int FUN = 3;
    int SELF = 2;
    int A = 3;
    int CAT(val, ue) = SQUARE(ID(BASE)) + FUN(0) + FUN + SELF + A + BONUS;
    EMPTY(ignored tokens)
#define SLOT left
#define INPUT 2
#include "include/slot.h"
#undef SLOT
#undef INPUT
#define SLOT right
#define INPUT 7
#include "include/slot.h"
    return CAT(val, ue) + left + right - 35;
}

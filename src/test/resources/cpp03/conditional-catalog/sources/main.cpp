#include "include/options.h"
#pragma cpg_preserved_marker
#
#if __cplusplus != 199711L
#error wrong C++ edition
#endif
#if 0
#include "deliberately-missing.h"
#if defined(SHOULD_NOT_CHECK)
#error skipped nested condition
#endif
#endif
int main() {
    int first = SWITCH_VALUE;
#undef SWITCH_VALUE
#define SWITCH_VALUE 2
    int second = SWITCH_VALUE;
    int chosen =
#ifdef DEBUG
        10
#else
        20
#endif
        + 1;
#if BROKEN
    int broken = 1 + ;
#endif
#line 120 "logical.cpp"
    int line = __LINE__;
    return chosen * 0 + first + second - 3 + line - 120;
}

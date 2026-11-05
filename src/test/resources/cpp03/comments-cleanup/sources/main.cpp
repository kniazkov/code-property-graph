#include "detail/value.h"
#define MULTI /* macro block first
macro block second */ 2
/* continued block comment \
this text is still a comment */
int physicalLine = __LINE__;
// first leading line
// second leading line
int value = 4;
int first = 1; int second = 2; // trailing last declaration
// separated file-only comment

int detached = 5;
int/**/joined = 6;
const char* literal = "/* this is text */ // still text";
int add(int left, int right) { return left + right; }
#ifdef FEATURE
/* enabled branch comment */
int chosen() { return FACTOR; }
#else
// disabled branch comment
int chosen() { return FACTOR + 1; }
#endif
int main() {
    int sum = add(first, /* second argument */ second);
    if (sum != 3 || value != 4 || detached != 5 || joined != 6) return 1;
    if (literal[0] != '/' || literal[1] != '*') return 2;
#ifdef FEATURE
    // active return check
    if (chosen() != 3) return 3;
#else
    /* inactive return check */
    if (chosen() != 4) return 4;
#endif
    if (MULTI != 2 || physicalLine != 6) return 5;
    return 0; // result explanation
}

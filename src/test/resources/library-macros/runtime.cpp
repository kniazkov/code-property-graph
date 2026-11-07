#include <climits>
#include <cfloat>
#include <cstddef>
#include <limits.h>
#include <float.h>
#include <stddef.h>
#if INT_MAX <= 0 || INT_MIN >= 0 || UINT_MAX <= INT_MAX || FLT_RADIX != 2
#error invalid target limits
#endif
int chosen = INT_MAX;
int main() {
    int* pointer = NULL;
    return chosen > 0 && pointer == NULL && LONG_MAX > 0L && ULONG_MAX > 0UL
        && FLT_MAX > 1.0F && DBL_MIN > 0.0 && LDBL_EPSILON > 0.0L ? 0 : 1;
}

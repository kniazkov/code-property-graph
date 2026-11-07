#include <cassert>
int count = 0;
int first() { assert(++count); return count; }
#define NDEBUG 0
#include <assert.h>
#define BAD(a,b) a+b
int disabled() { assert(BAD(1)); assert(undeclared++); return count; }
#undef NDEBUG
#include <cassert>
int last() { assert(++count); return count; }
int main() { first(); disabled(); last(); return count == 2 ? 0 : 1; }

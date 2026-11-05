#include "include/config.h"
static int helper() { return 10; }
static int offset = 0;
namespace { int hidden() { return 1; } }
int a() { extern int offset; int helper(); Width sample = 0; return helper() + hidden() + offset; }

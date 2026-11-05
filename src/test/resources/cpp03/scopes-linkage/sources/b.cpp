#include "include/config.h"
static int helper() { return 20; }
static int offset = 0;
namespace { int hidden() { return 2; } }
int b() { extern int offset; int helper(); Width sample = 0; return helper() + hidden() + offset; }

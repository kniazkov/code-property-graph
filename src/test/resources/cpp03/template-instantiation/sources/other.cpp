#include <vector>
#include "api.h"
template int identity<int>(int);
int choose(...) { return 5; }
int other() { std::vector<int> values(2); return outer(values) + identity(7); }

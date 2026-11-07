#include <cassert>
#include <cstdio>
#include <cstdlib>
struct Guard { ~Guard() { std::puts("UNWOUND"); std::fflush(0); } };
void on_exit() { std::puts("ATEXIT"); std::fflush(0); }
#define ZERO 0
#line 73 "assertion-input.cpp"
int main() { std::atexit(on_exit); Guard guard; assert(ZERO); std::puts("AFTER_ASSERT"); std::fflush(0); return 0; }

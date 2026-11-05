// Deterministic C++03 C-library examples only. MIT License.
#include <cstddef>
#include <cstdlib>
#include <cstring>
#include <cctype>
#include <cmath>
#include <cwchar>
#include <cwctype>
#include <cstdarg>
#include <cassert>
#include <ciso646>

int sum(int count, ...) {
    std::va_list args;
    va_start(args, count);
    int result = 0;
    for (int i = 0; i < count; ++i) result += va_arg(args, int);
    va_end(args);
    return result;
}
int main() {
    char text[8] = "abc";
    const char* constant = text;
    if (std::strchr(text, 'b') != text+1 or std::strchr(constant, 'b') != constant+1) return 1;
    if (std::strlen(text) != 3 or std::strcmp(text, "abc") != 0) return 2;
    const std::div_t quotient = std::div(7, 3);
    if (quotient.quot != 2 or quotient.rem != 1) return 3;
    if (std::abs(-3L) != 3L or std::sqrt(4.0) != 2.0) return 4;
    // The startup C locale guarantees these character tests; no locale mutation.
    if (not std::isdigit('7') or std::toupper('a') != 'A') return 5;
    if (std::wcslen(L"wide") != 4 or not std::iswdigit(L'7')) return 6;
    if (sum(3, 1, 2, 3) != 6) return 7;
    assert(std::strlen(text) == 3);
    return 0;
}

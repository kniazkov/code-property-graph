// C++03 signature-only probe. No process, signal, file or nonlocal-jump operation executes.
// Macro assertions below verify presence, never a guessed target value.
#include <cstddef>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cctype>
#include <cmath>
#include <clocale>
#include <ctime>
#include <cwchar>
#include <cwctype>
#include <csignal>
#include <csetjmp>
#include <cstdarg>
#include <cassert>
#include <cerrno>
#include <climits>
#include <cfloat>
#include <ciso646>
#ifndef assert
#error Missing C++03 macro assert
#endif
#ifndef errno
#error Missing C++03 macro errno
#endif
#ifndef CHAR_BIT
#error Missing C++03 macro CHAR_BIT
#endif
#ifndef INT_MAX
#error Missing C++03 macro INT_MAX
#endif
#ifndef FLT_MAX
#error Missing C++03 macro FLT_MAX
#endif
#ifndef DBL_EPSILON
#error Missing C++03 macro DBL_EPSILON
#endif
#ifndef NULL
#error Missing C++03 macro NULL
#endif
#ifndef offsetof
#error Missing C++03 macro offsetof
#endif
#ifndef EOF
#error Missing C++03 macro EOF
#endif
#ifndef CLOCKS_PER_SEC
#error Missing C++03 macro CLOCKS_PER_SEC
#endif
#ifndef SIGABRT
#error Missing C++03 macro SIGABRT
#endif
#ifndef setjmp
#error Missing C++03 macro setjmp
#endif
#ifndef va_start
#error Missing C++03 macro va_start
#endif
#ifndef va_arg
#error Missing C++03 macro va_arg
#endif
#ifndef va_end
#error Missing C++03 macro va_end
#endif
namespace signatures {
typedef void (*handler)(int);
handler (*signal_signature)(int, handler) = &std::signal;
int (*p0)(const char*, ...) = &std::printf;
int (*p1)(std::FILE*, const char*, ...) = &std::fprintf;
int (*p2)(char*, const char*, ...) = &std::sprintf;
int (*p3)(const char*, const char*, ...) = &std::sscanf;
int (*p4)(const char*, std::va_list) = &std::vprintf;
int (*p5)(std::FILE*, const char*, std::va_list) = &std::vfprintf;
int (*p6)(char*, const char*, std::va_list) = &std::vsprintf;
std::FILE* (*p7)(const char*, const char*) = &std::fopen;
std::FILE* (*p8)(const char*, const char*, std::FILE*) = &std::freopen;
std::FILE* (*p9)() = &std::tmpfile;
int (*p10)(std::FILE*, std::fpos_t*) = &std::fgetpos;
int (*p11)(std::FILE*, const std::fpos_t*) = &std::fsetpos;
std::size_t (*p12)(void*, std::size_t, std::size_t, std::FILE*) = &std::fread;
std::size_t (*p13)(const void*, std::size_t, std::size_t, std::FILE*) = &std::fwrite;
int (*p14)(std::FILE*, char*, int, std::size_t) = &std::setvbuf;
double (*p15)(const char*, char**) = &std::strtod;
long (*p16)(const char*, char**, int) = &std::strtol;
unsigned long (*p17)(const char*, char**, int) = &std::strtoul;
int (*p18)(int) = &std::abs;
long (*p19)(long) = &std::abs;
std::div_t (*p20)(int, int) = &std::div;
std::ldiv_t (*p21)(long, long) = &std::div;
void* (*p22)(std::size_t, std::size_t) = &std::calloc;
void* (*p23)(void*, std::size_t) = &std::realloc;
void (*p24)(void*) = &std::free;
int (*p25)(void (*)()) = &std::atexit;
void (*p26)(int) = &std::exit;
void (*p27)() = &std::abort;
int (*p28)(const char*) = &std::system;
void* (*p29)(const void*, const void*, std::size_t, std::size_t, int (*)(const void*, const void*)) = &std::bsearch;
void (*p30)(void*, std::size_t, std::size_t, int (*)(const void*, const void*)) = &std::qsort;
std::size_t (*p31)(wchar_t*, const char*, std::size_t) = &std::mbstowcs;
std::size_t (*p32)(char*, const wchar_t*, std::size_t) = &std::wcstombs;
void* (*p33)(void*, const void*, std::size_t) = &std::memcpy;
void* (*p34)(void*, const void*, std::size_t) = &std::memmove;
std::size_t (*p35)(const char*) = &std::strlen;
int (*p36)(const char*, const char*) = &std::strcmp;
char* (*p37)(char*, const char*) = &std::strtok;
std::size_t (*p38)(char*, const char*, std::size_t) = &std::strxfrm;
std::lconv* (*p39)() = &std::localeconv;
char* (*p40)(int, const char*) = &std::setlocale;
std::clock_t (*p41)() = &std::clock;
std::time_t (*p42)(std::time_t*) = &std::time;
std::time_t (*p43)(std::tm*) = &std::mktime;
double (*p44)(std::time_t, std::time_t) = &std::difftime;
std::tm* (*p45)(const std::time_t*) = &std::gmtime;
std::tm* (*p46)(const std::time_t*) = &std::localtime;
std::size_t (*p47)(char*, std::size_t, const char*, const std::tm*) = &std::strftime;
int (*p48)(std::FILE*, const wchar_t*, ...) = &std::fwprintf;
int (*p49)(wchar_t*, std::size_t, const wchar_t*, ...) = &std::swprintf;
int (*p50)(std::FILE*, const wchar_t*, std::va_list) = &std::vfwprintf;
int (*p51)(wchar_t*, std::size_t, const wchar_t*, std::va_list) = &std::vswprintf;
std::wint_t (*p52)(std::wint_t, std::FILE*) = &std::ungetwc;
double (*p53)(const wchar_t*, wchar_t**) = &std::wcstod;
long (*p54)(const wchar_t*, wchar_t**, int) = &std::wcstol;
unsigned long (*p55)(const wchar_t*, wchar_t**, int) = &std::wcstoul;
wchar_t* (*p56)(wchar_t*, const wchar_t*, wchar_t**) = &std::wcstok;
std::size_t (*p57)(wchar_t*, std::size_t, const wchar_t*, const std::tm*) = &std::wcsftime;
std::size_t (*p58)(wchar_t*, const char*, std::size_t, std::mbstate_t*) = &std::mbrtowc;
std::size_t (*p59)(char*, wchar_t, std::mbstate_t*) = &std::wcrtomb;
std::size_t (*p60)(wchar_t*, const char**, std::size_t, std::mbstate_t*) = &std::mbsrtowcs;
std::size_t (*p61)(char*, const wchar_t**, std::size_t, std::mbstate_t*) = &std::wcsrtombs;
int (*p62)(const std::mbstate_t*) = &std::mbsinit;
std::wctype_t (*p63)(const char*) = &std::wctype;
std::wctrans_t (*p64)(const char*) = &std::wctrans;
int (*p65)(std::wint_t, std::wctype_t) = &std::iswctype;
std::wint_t (*p66)(std::wint_t, std::wctrans_t) = &std::towctrans;
int (*p67)(int) = &std::raise;
void (*p68)(std::jmp_buf, int) = &std::longjmp;
int (*p69)(int) = &std::isalnum;
int (*p70)(std::wint_t) = &std::iswalnum;
int (*p71)(int) = &std::isalpha;
int (*p72)(std::wint_t) = &std::iswalpha;
int (*p73)(int) = &std::iscntrl;
int (*p74)(std::wint_t) = &std::iswcntrl;
int (*p75)(int) = &std::isdigit;
int (*p76)(std::wint_t) = &std::iswdigit;
int (*p77)(int) = &std::isgraph;
int (*p78)(std::wint_t) = &std::iswgraph;
int (*p79)(int) = &std::islower;
int (*p80)(std::wint_t) = &std::iswlower;
int (*p81)(int) = &std::isprint;
int (*p82)(std::wint_t) = &std::iswprint;
int (*p83)(int) = &std::ispunct;
int (*p84)(std::wint_t) = &std::iswpunct;
int (*p85)(int) = &std::isspace;
int (*p86)(std::wint_t) = &std::iswspace;
int (*p87)(int) = &std::isupper;
int (*p88)(std::wint_t) = &std::iswupper;
int (*p89)(int) = &std::isxdigit;
int (*p90)(std::wint_t) = &std::iswxdigit;
int (*p91)(int) = &std::tolower;
std::wint_t (*p92)(std::wint_t) = &std::towlower;
int (*p93)(int) = &std::toupper;
std::wint_t (*p94)(std::wint_t) = &std::towupper;
float (*p95)(float) = &std::acos;
float (*p96)(float) = &std::asin;
float (*p97)(float) = &std::atan;
float (*p98)(float) = &std::cos;
float (*p99)(float) = &std::sin;
float (*p100)(float) = &std::tan;
float (*p101)(float) = &std::cosh;
float (*p102)(float) = &std::sinh;
float (*p103)(float) = &std::tanh;
float (*p104)(float) = &std::exp;
float (*p105)(float) = &std::log;
float (*p106)(float) = &std::log10;
float (*p107)(float) = &std::sqrt;
float (*p108)(float) = &std::ceil;
float (*p109)(float) = &std::fabs;
float (*p110)(float) = &std::floor;
float (*p111)(float) = &std::abs;
float (*p112)(float, float) = &std::atan2;
float (*p113)(float, float) = &std::pow;
float (*p114)(float, float) = &std::fmod;
float (*p115)(float, int) = &std::pow;
float (*p116)(float, int*) = &std::frexp;
float (*p117)(float, int) = &std::ldexp;
float (*p118)(float, float*) = &std::modf;
double (*p119)(double) = &std::acos;
double (*p120)(double) = &std::asin;
double (*p121)(double) = &std::atan;
double (*p122)(double) = &std::cos;
double (*p123)(double) = &std::sin;
double (*p124)(double) = &std::tan;
double (*p125)(double) = &std::cosh;
double (*p126)(double) = &std::sinh;
double (*p127)(double) = &std::tanh;
double (*p128)(double) = &std::exp;
double (*p129)(double) = &std::log;
double (*p130)(double) = &std::log10;
double (*p131)(double) = &std::sqrt;
double (*p132)(double) = &std::ceil;
double (*p133)(double) = &std::fabs;
double (*p134)(double) = &std::floor;
double (*p135)(double) = &std::abs;
double (*p136)(double, double) = &std::atan2;
double (*p137)(double, double) = &std::pow;
double (*p138)(double, double) = &std::fmod;
double (*p139)(double, int) = &std::pow;
double (*p140)(double, int*) = &std::frexp;
double (*p141)(double, int) = &std::ldexp;
double (*p142)(double, double*) = &std::modf;
long double (*p143)(long double) = &std::acos;
long double (*p144)(long double) = &std::asin;
long double (*p145)(long double) = &std::atan;
long double (*p146)(long double) = &std::cos;
long double (*p147)(long double) = &std::sin;
long double (*p148)(long double) = &std::tan;
long double (*p149)(long double) = &std::cosh;
long double (*p150)(long double) = &std::sinh;
long double (*p151)(long double) = &std::tanh;
long double (*p152)(long double) = &std::exp;
long double (*p153)(long double) = &std::log;
long double (*p154)(long double) = &std::log10;
long double (*p155)(long double) = &std::sqrt;
long double (*p156)(long double) = &std::ceil;
long double (*p157)(long double) = &std::fabs;
long double (*p158)(long double) = &std::floor;
long double (*p159)(long double) = &std::abs;
long double (*p160)(long double, long double) = &std::atan2;
long double (*p161)(long double, long double) = &std::pow;
long double (*p162)(long double, long double) = &std::fmod;
long double (*p163)(long double, int) = &std::pow;
long double (*p164)(long double, int*) = &std::frexp;
long double (*p165)(long double, int) = &std::ldexp;
long double (*p166)(long double, long double*) = &std::modf;
char* (*p167)(char*, int) = &std::strchr;
const char* (*p168)(const char*, int) = &std::strchr;
char* (*p169)(char*, int) = &std::strrchr;
const char* (*p170)(const char*, int) = &std::strrchr;
char* (*p171)(char*, const char*) = &std::strstr;
const char* (*p172)(const char*, const char*) = &std::strstr;
char* (*p173)(char*, const char*) = &std::strpbrk;
const char* (*p174)(const char*, const char*) = &std::strpbrk;
void* (*p175)(void*, int, std::size_t) = &std::memchr;
const void* (*p176)(const void*, int, std::size_t) = &std::memchr;
// glibc with Clang compatibility exposes legacy mutable wide-search returns.
#ifndef __clang__
const wchar_t* (*wide_const)(const wchar_t*, wchar_t) = &std::wcschr;
#endif
wchar_t* (*wide_mutable)(wchar_t*, wchar_t) = &std::wcschr;
}
// Alternative tokens are language keywords in C++, not required macro definitions.
bool alternative_tokens(bool a, bool b) { return a and not b; }

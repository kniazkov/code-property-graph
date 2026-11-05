// C++03 signature-only probe. No process, signal, file or nonlocal-jump operation executes.
// Macro assertions below verify presence, never a guessed target value.
// CPATH can classify GCC limits.h as a user header under Clang.
#ifdef __clang__
#pragma clang diagnostic ignored "-Wgnu-include-next"
#endif
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <math.h>
#include <locale.h>
#include <time.h>
#include <wchar.h>
#include <wctype.h>
#include <signal.h>
#include <setjmp.h>
#include <stdarg.h>
#include <assert.h>
#include <errno.h>
#include <limits.h>
#include <float.h>
#include <iso646.h>
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
handler (*signal_signature)(int, handler) = &signal;
int (*p0)(const char*, ...) = &printf;
int (*p1)(FILE*, const char*, ...) = &fprintf;
int (*p2)(char*, const char*, ...) = &sprintf;
int (*p3)(const char*, const char*, ...) = &sscanf;
int (*p4)(const char*, va_list) = &vprintf;
int (*p5)(FILE*, const char*, va_list) = &vfprintf;
int (*p6)(char*, const char*, va_list) = &vsprintf;
FILE* (*p7)(const char*, const char*) = &fopen;
FILE* (*p8)(const char*, const char*, FILE*) = &freopen;
FILE* (*p9)() = &tmpfile;
int (*p10)(FILE*, fpos_t*) = &fgetpos;
int (*p11)(FILE*, const fpos_t*) = &fsetpos;
size_t (*p12)(void*, size_t, size_t, FILE*) = &fread;
size_t (*p13)(const void*, size_t, size_t, FILE*) = &fwrite;
int (*p14)(FILE*, char*, int, size_t) = &setvbuf;
double (*p15)(const char*, char**) = &strtod;
long (*p16)(const char*, char**, int) = &strtol;
unsigned long (*p17)(const char*, char**, int) = &strtoul;
int (*p18)(int) = &abs;
long (*p19)(long) = &abs;
div_t (*p20)(int, int) = &div;
ldiv_t (*p21)(long, long) = &div;
void* (*p22)(size_t, size_t) = &calloc;
void* (*p23)(void*, size_t) = &realloc;
void (*p24)(void*) = &free;
int (*p25)(void (*)()) = &atexit;
void (*p26)(int) = &exit;
void (*p27)() = &abort;
int (*p28)(const char*) = &system;
void* (*p29)(const void*, const void*, size_t, size_t, int (*)(const void*, const void*)) = &bsearch;
void (*p30)(void*, size_t, size_t, int (*)(const void*, const void*)) = &qsort;
size_t (*p31)(wchar_t*, const char*, size_t) = &mbstowcs;
size_t (*p32)(char*, const wchar_t*, size_t) = &wcstombs;
void* (*p33)(void*, const void*, size_t) = &memcpy;
void* (*p34)(void*, const void*, size_t) = &memmove;
size_t (*p35)(const char*) = &strlen;
int (*p36)(const char*, const char*) = &strcmp;
char* (*p37)(char*, const char*) = &strtok;
size_t (*p38)(char*, const char*, size_t) = &strxfrm;
lconv* (*p39)() = &localeconv;
char* (*p40)(int, const char*) = &setlocale;
clock_t (*p41)() = &clock;
time_t (*p42)(time_t*) = &time;
time_t (*p43)(tm*) = &mktime;
double (*p44)(time_t, time_t) = &difftime;
tm* (*p45)(const time_t*) = &gmtime;
tm* (*p46)(const time_t*) = &localtime;
size_t (*p47)(char*, size_t, const char*, const tm*) = &strftime;
int (*p48)(FILE*, const wchar_t*, ...) = &fwprintf;
int (*p49)(wchar_t*, size_t, const wchar_t*, ...) = &swprintf;
int (*p50)(FILE*, const wchar_t*, va_list) = &vfwprintf;
int (*p51)(wchar_t*, size_t, const wchar_t*, va_list) = &vswprintf;
wint_t (*p52)(wint_t, FILE*) = &ungetwc;
double (*p53)(const wchar_t*, wchar_t**) = &wcstod;
long (*p54)(const wchar_t*, wchar_t**, int) = &wcstol;
unsigned long (*p55)(const wchar_t*, wchar_t**, int) = &wcstoul;
wchar_t* (*p56)(wchar_t*, const wchar_t*, wchar_t**) = &wcstok;
size_t (*p57)(wchar_t*, size_t, const wchar_t*, const tm*) = &wcsftime;
size_t (*p58)(wchar_t*, const char*, size_t, mbstate_t*) = &mbrtowc;
size_t (*p59)(char*, wchar_t, mbstate_t*) = &wcrtomb;
size_t (*p60)(wchar_t*, const char**, size_t, mbstate_t*) = &mbsrtowcs;
size_t (*p61)(char*, const wchar_t**, size_t, mbstate_t*) = &wcsrtombs;
int (*p62)(const mbstate_t*) = &mbsinit;
wctype_t (*p63)(const char*) = &wctype;
wctrans_t (*p64)(const char*) = &wctrans;
int (*p65)(wint_t, wctype_t) = &iswctype;
wint_t (*p66)(wint_t, wctrans_t) = &towctrans;
int (*p67)(int) = &raise;
void (*p68)(jmp_buf, int) = &longjmp;
int (*p69)(int) = &isalnum;
int (*p70)(wint_t) = &iswalnum;
int (*p71)(int) = &isalpha;
int (*p72)(wint_t) = &iswalpha;
int (*p73)(int) = &iscntrl;
int (*p74)(wint_t) = &iswcntrl;
int (*p75)(int) = &isdigit;
int (*p76)(wint_t) = &iswdigit;
int (*p77)(int) = &isgraph;
int (*p78)(wint_t) = &iswgraph;
int (*p79)(int) = &islower;
int (*p80)(wint_t) = &iswlower;
int (*p81)(int) = &isprint;
int (*p82)(wint_t) = &iswprint;
int (*p83)(int) = &ispunct;
int (*p84)(wint_t) = &iswpunct;
int (*p85)(int) = &isspace;
int (*p86)(wint_t) = &iswspace;
int (*p87)(int) = &isupper;
int (*p88)(wint_t) = &iswupper;
int (*p89)(int) = &isxdigit;
int (*p90)(wint_t) = &iswxdigit;
int (*p91)(int) = &tolower;
wint_t (*p92)(wint_t) = &towlower;
int (*p93)(int) = &toupper;
wint_t (*p94)(wint_t) = &towupper;
float (*p95)(float) = &acos;
float (*p96)(float) = &asin;
float (*p97)(float) = &atan;
float (*p98)(float) = &cos;
float (*p99)(float) = &sin;
float (*p100)(float) = &tan;
float (*p101)(float) = &cosh;
float (*p102)(float) = &sinh;
float (*p103)(float) = &tanh;
float (*p104)(float) = &exp;
float (*p105)(float) = &log;
float (*p106)(float) = &log10;
float (*p107)(float) = &sqrt;
float (*p108)(float) = &ceil;
float (*p109)(float) = &fabs;
float (*p110)(float) = &floor;
float (*p111)(float) = &abs;
float (*p112)(float, float) = &atan2;
float (*p113)(float, float) = &pow;
float (*p114)(float, float) = &fmod;
float (*p115)(float, int) = &pow;
float (*p116)(float, int*) = &frexp;
float (*p117)(float, int) = &ldexp;
float (*p118)(float, float*) = &modf;
double (*p119)(double) = &acos;
double (*p120)(double) = &asin;
double (*p121)(double) = &atan;
double (*p122)(double) = &cos;
double (*p123)(double) = &sin;
double (*p124)(double) = &tan;
double (*p125)(double) = &cosh;
double (*p126)(double) = &sinh;
double (*p127)(double) = &tanh;
double (*p128)(double) = &exp;
double (*p129)(double) = &log;
double (*p130)(double) = &log10;
double (*p131)(double) = &sqrt;
double (*p132)(double) = &ceil;
double (*p133)(double) = &fabs;
double (*p134)(double) = &floor;
double (*p135)(double) = &abs;
double (*p136)(double, double) = &atan2;
double (*p137)(double, double) = &pow;
double (*p138)(double, double) = &fmod;
double (*p139)(double, int) = &pow;
double (*p140)(double, int*) = &frexp;
double (*p141)(double, int) = &ldexp;
double (*p142)(double, double*) = &modf;
long double (*p143)(long double) = &acos;
long double (*p144)(long double) = &asin;
long double (*p145)(long double) = &atan;
long double (*p146)(long double) = &cos;
long double (*p147)(long double) = &sin;
long double (*p148)(long double) = &tan;
long double (*p149)(long double) = &cosh;
long double (*p150)(long double) = &sinh;
long double (*p151)(long double) = &tanh;
long double (*p152)(long double) = &exp;
long double (*p153)(long double) = &log;
long double (*p154)(long double) = &log10;
long double (*p155)(long double) = &sqrt;
long double (*p156)(long double) = &ceil;
long double (*p157)(long double) = &fabs;
long double (*p158)(long double) = &floor;
long double (*p159)(long double) = &abs;
long double (*p160)(long double, long double) = &atan2;
long double (*p161)(long double, long double) = &pow;
long double (*p162)(long double, long double) = &fmod;
long double (*p163)(long double, int) = &pow;
long double (*p164)(long double, int*) = &frexp;
long double (*p165)(long double, int) = &ldexp;
long double (*p166)(long double, long double*) = &modf;
char* (*p167)(char*, int) = &strchr;
const char* (*p168)(const char*, int) = &strchr;
char* (*p169)(char*, int) = &strrchr;
const char* (*p170)(const char*, int) = &strrchr;
char* (*p171)(char*, const char*) = &strstr;
const char* (*p172)(const char*, const char*) = &strstr;
char* (*p173)(char*, const char*) = &strpbrk;
const char* (*p174)(const char*, const char*) = &strpbrk;
void* (*p175)(void*, int, size_t) = &memchr;
const void* (*p176)(const void*, int, size_t) = &memchr;
// glibc with Clang compatibility exposes legacy mutable wide-search returns.
#ifndef __clang__
const wchar_t* (*wide_const)(const wchar_t*, wchar_t) = &wcschr;
wchar_t* (*wide_mutable)(wchar_t*, wchar_t) = &wcschr;
#endif
}
// Alternative tokens are language keywords in C++, not required macro definitions.
bool alternative_tokens(bool a, bool b) { return a and not b; }

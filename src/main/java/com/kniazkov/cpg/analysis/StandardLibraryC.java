/* Portable C++03 C-library declarations, with implementation-defined types kept opaque. MIT License. */
package com.kniazkov.cpg.analysis;
import java.util.Set;
final class StandardLibraryC {
    private StandardLibraryC() {}
    static boolean supports(String h) { return Set.of("cassert","cctype","cerrno","cfloat","ciso646","climits","clocale","cmath","csetjmp","csignal","cstdarg","cstdio","cstdlib","cstring","ctime","cwchar","cwctype").contains(h); }
    static Set<String> dependencies(String h) { return switch(h) {
        case "cstdio" -> Set.of("cstddef","cstdarg");
        case "cstdlib","cstring","ctime","clocale","cstdarg","cwctype" -> Set.of("cstddef");
        case "cwchar" -> Set.of("cstddef","cstdio","ctime","cstdarg");
        default -> Set.of();
    }; }
    static Set<String> templateNames(String h) { return Set.of(); }
    static Set<String> typeNames(String h) { return switch(h) {
        case "clocale" -> Set.of("lconv"); case "csetjmp" -> Set.of("jmp_buf"); case "csignal" -> Set.of("sig_atomic_t"); case "cstdarg" -> Set.of("va_list");
        case "cstdio" -> Set.of("FILE","fpos_t"); case "cstdlib" -> Set.of("div_t","ldiv_t"); case "ctime" -> Set.of("clock_t","time_t","tm");
        case "cwchar" -> Set.of("mbstate_t","wint_t"); case "cwctype" -> Set.of("wint_t","wctrans_t","wctype_t"); default -> Set.of(); }; }
    static String declarations(String h) { return switch(h) {
        case "cassert","cerrno","cfloat","ciso646","climits" -> "";
        case "cctype" -> characterFunctions("int", "int", "");
        case "clocale" -> """
struct lconv { char* decimal_point; char* thousands_sep; char* grouping; char* int_curr_symbol; char* currency_symbol; char* mon_decimal_point; char* mon_thousands_sep; char* mon_grouping; char* positive_sign; char* negative_sign; char int_frac_digits; char frac_digits; char p_cs_precedes; char p_sep_by_space; char n_cs_precedes; char n_sep_by_space; char p_sign_posn; char n_sign_posn; };
char* setlocale(int, const char*); lconv* localeconv();
""";
        case "csetjmp" -> "typedef __cpg_opaque_object_jmp_buf jmp_buf; void longjmp(jmp_buf, int);";
        case "csignal" -> "typedef __cpg_opaque_integer_sig_atomic_t sig_atomic_t; void (*signal(int, void (*)(int)))(int); int raise(int);";
        case "cstdarg" -> "typedef __cpg_opaque_object_va_list va_list;";
        case "cstdio" -> """
typedef __cpg_opaque_object_FILE FILE; typedef __cpg_opaque_object_fpos_t fpos_t;
int remove(const char*); int rename(const char*, const char*); FILE* tmpfile(); char* tmpnam(char*);
int fclose(FILE*); int fflush(FILE*); FILE* fopen(const char*, const char*); FILE* freopen(const char*, const char*, FILE*); void setbuf(FILE*, char*); int setvbuf(FILE*, char*, int, size_t);
int fprintf(FILE*, const char*, ...); int fscanf(FILE*, const char*, ...); int printf(const char*, ...); int scanf(const char*, ...); int sprintf(char*, const char*, ...); int sscanf(const char*, const char*, ...);
int vfprintf(FILE*, const char*, va_list); int vprintf(const char*, va_list); int vsprintf(char*, const char*, va_list);
int fgetc(FILE*); char* fgets(char*, int, FILE*); int fputc(int, FILE*); int fputs(const char*, FILE*); int getc(FILE*); int getchar(); char* gets(char*); int putc(int, FILE*); int putchar(int); int puts(const char*); int ungetc(int, FILE*);
size_t fread(void*, size_t, size_t, FILE*); size_t fwrite(const void*, size_t, size_t, FILE*);
int fgetpos(FILE*, fpos_t*); int fseek(FILE*, long, int); int fsetpos(FILE*, const fpos_t*); long ftell(FILE*); void rewind(FILE*);
void clearerr(FILE*); int feof(FILE*); int ferror(FILE*); void perror(const char*);
""";
        case "cstdlib" -> """
struct div_t { int quot; int rem; }; struct ldiv_t { long quot; long rem; };
double atof(const char*); int atoi(const char*); long atol(const char*); double strtod(const char*, char**); long strtol(const char*, char**, int); unsigned long strtoul(const char*, char**, int);
int rand(); void srand(unsigned int); void* calloc(size_t, size_t); void free(void*); void* malloc(size_t); void* realloc(void*, size_t);
void abort(); int atexit(void (*)()); void exit(int); char* getenv(const char*); int system(const char*);
void* bsearch(const void*, const void*, size_t, size_t, int (*)(const void*, const void*)); void qsort(void*, size_t, size_t, int (*)(const void*, const void*));
int abs(int); long abs(long); long labs(long); div_t div(int, int); ldiv_t div(long, long); ldiv_t ldiv(long, long);
int mblen(const char*, size_t); int mbtowc(wchar_t*, const char*, size_t); int wctomb(char*, wchar_t); size_t mbstowcs(wchar_t*, const char*, size_t); size_t wcstombs(char*, const wchar_t*, size_t);
""";
        case "cstring" -> """
void* memcpy(void*, const void*, size_t); void* memmove(void*, const void*, size_t); char* strcpy(char*, const char*); char* strncpy(char*, const char*, size_t); char* strcat(char*, const char*); char* strncat(char*, const char*, size_t);
int memcmp(const void*, const void*, size_t); int strcmp(const char*, const char*); int strcoll(const char*, const char*); int strncmp(const char*, const char*, size_t); size_t strxfrm(char*, const char*, size_t);
const void* memchr(const void*, int, size_t); void* memchr(void*, int, size_t); const char* strchr(const char*, int); char* strchr(char*, int); size_t strcspn(const char*, const char*); const char* strpbrk(const char*, const char*); char* strpbrk(char*, const char*); const char* strrchr(const char*, int); char* strrchr(char*, int); size_t strspn(const char*, const char*); const char* strstr(const char*, const char*); char* strstr(char*, const char*); char* strtok(char*, const char*);
void* memset(void*, int, size_t); char* strerror(int); size_t strlen(const char*);
""";
        case "ctime" -> """
typedef __cpg_opaque_arithmetic_clock_t clock_t; typedef __cpg_opaque_arithmetic_time_t time_t;
struct tm { int tm_sec; int tm_min; int tm_hour; int tm_mday; int tm_mon; int tm_year; int tm_wday; int tm_yday; int tm_isdst; };
clock_t clock(); double difftime(time_t, time_t); time_t mktime(tm*); time_t time(time_t*); char* asctime(const tm*); char* ctime(const time_t*); tm* gmtime(const time_t*); tm* localtime(const time_t*); size_t strftime(char*, size_t, const char*, const tm*);
""";
        case "cwchar" -> wide();
        case "cwctype" -> "typedef __cpg_opaque_integer_wint_t wint_t; typedef __cpg_opaque_unspecified_wctrans_t wctrans_t; typedef __cpg_opaque_unspecified_wctype_t wctype_t;"+characterFunctions("int","wint_t","w")+"int iswctype(wint_t, wctype_t); wctype_t wctype(const char*); wint_t towctrans(wint_t, wctrans_t); wctrans_t wctrans(const char*);";
        case "cmath" -> math();
        default -> null;
    }; }
    private static String characterFunctions(String result,String argument,String wide) {
        var s=new StringBuilder(); for(String n:new String[]{"alnum","alpha","cntrl","digit","graph","lower","print","punct","space","upper","xdigit"})s.append(result).append(" is").append(wide).append(n).append("(").append(argument).append(");");
        for(String n:new String[]{"lower","upper"})s.append(argument).append(" to").append(wide).append(n).append("(").append(argument).append(");");return s.toString();
    }
    private static String math() {
        var s=new StringBuilder();for(String t:new String[]{"float","double","long double"}) {
            for(String name:new String[]{"acos","asin","atan","cos","sin","tan","cosh","sinh","tanh","exp","log","log10","sqrt","ceil","fabs","floor","abs"})s.append(t).append(' ').append(name).append('(').append(t).append(");");
            for(String name:new String[]{"atan2","pow","fmod"})s.append(t).append(' ').append(name).append('(').append(t).append(',').append(t).append(");");
            s.append(t).append(" pow(").append(t).append(",int);").append(t).append(" frexp(").append(t).append(",int*);").append(t).append(" ldexp(").append(t).append(",int);").append(t).append(" modf(").append(t).append(',').append(t).append("*);");
        }return s.toString();
    }
    private static String wide() { return """
typedef __cpg_opaque_object_mbstate_t mbstate_t; typedef __cpg_opaque_integer_wint_t wint_t;
int fwprintf(FILE*, const wchar_t*, ...); int fwscanf(FILE*, const wchar_t*, ...); int swprintf(wchar_t*, size_t, const wchar_t*, ...); int swscanf(const wchar_t*, const wchar_t*, ...); int wprintf(const wchar_t*, ...); int wscanf(const wchar_t*, ...);
int vfwprintf(FILE*, const wchar_t*, va_list); int vswprintf(wchar_t*, size_t, const wchar_t*, va_list); int vwprintf(const wchar_t*, va_list);
wint_t fgetwc(FILE*); wchar_t* fgetws(wchar_t*, int, FILE*); wint_t fputwc(wchar_t, FILE*); int fputws(const wchar_t*, FILE*); int fwide(FILE*, int); wint_t getwc(FILE*); wint_t getwchar(); wint_t putwc(wchar_t, FILE*); wint_t putwchar(wchar_t); wint_t ungetwc(wint_t, FILE*);
double wcstod(const wchar_t*, wchar_t**); long wcstol(const wchar_t*, wchar_t**, int); unsigned long wcstoul(const wchar_t*, wchar_t**, int);
wchar_t* wcscpy(wchar_t*, const wchar_t*); wchar_t* wcsncpy(wchar_t*, const wchar_t*, size_t); wchar_t* wmemcpy(wchar_t*, const wchar_t*, size_t); wchar_t* wmemmove(wchar_t*, const wchar_t*, size_t); wchar_t* wcscat(wchar_t*, const wchar_t*); wchar_t* wcsncat(wchar_t*, const wchar_t*, size_t);
int wcscmp(const wchar_t*, const wchar_t*); int wcscoll(const wchar_t*, const wchar_t*); int wcsncmp(const wchar_t*, const wchar_t*, size_t); size_t wcsxfrm(wchar_t*, const wchar_t*, size_t); int wmemcmp(const wchar_t*, const wchar_t*, size_t);
const wchar_t* wcschr(const wchar_t*, wchar_t); wchar_t* wcschr(wchar_t*, wchar_t); size_t wcscspn(const wchar_t*, const wchar_t*); size_t wcslen(const wchar_t*); const wchar_t* wcspbrk(const wchar_t*, const wchar_t*); wchar_t* wcspbrk(wchar_t*, const wchar_t*); const wchar_t* wcsrchr(const wchar_t*, wchar_t); wchar_t* wcsrchr(wchar_t*, wchar_t); size_t wcsspn(const wchar_t*, const wchar_t*); const wchar_t* wcsstr(const wchar_t*, const wchar_t*); wchar_t* wcsstr(wchar_t*, const wchar_t*); wchar_t* wcstok(wchar_t*, const wchar_t*, wchar_t**); const wchar_t* wmemchr(const wchar_t*, wchar_t, size_t); wchar_t* wmemchr(wchar_t*, wchar_t, size_t); wchar_t* wmemset(wchar_t*, wchar_t, size_t);
size_t wcsftime(wchar_t*, size_t, const wchar_t*, const tm*); wint_t btowc(int); int wctob(wint_t); int mbsinit(const mbstate_t*); size_t mbrlen(const char*, size_t, mbstate_t*); size_t mbrtowc(wchar_t*, const char*, size_t, mbstate_t*); size_t wcrtomb(char*, wchar_t, mbstate_t*); size_t mbsrtowcs(wchar_t*, const char**, size_t, mbstate_t*); size_t wcsrtombs(char*, const wchar_t**, size_t, mbstate_t*);
"""; }
}

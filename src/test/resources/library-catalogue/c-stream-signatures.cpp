#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <cwchar>
#include <cwctype>
#include <cmath>
#include <csignal>
#include <sstream>
#include <fstream>
#include <locale>
#include <iomanip>
namespace {
int (*vprint)(std::FILE*,const char*,std::va_list)=&std::vfprintf;
std::size_t (*restart)(wchar_t*,const char*,std::size_t,std::mbstate_t*)=&std::mbrtowc;
const char* (*find_const)(const char*,int)=&std::strchr;
char* (*find_mutable)(char*,int)=&std::strchr;
// glibc exposes the legacy mutable return with Clang's GCC compatibility macros.
// Keep this exact const-overload assertion on GCC; the remaining probe runs on both.
#ifndef __clang__
const wchar_t* (*wide_find_const)(const wchar_t*,wchar_t)=&std::wcschr;
#endif
wchar_t* (*wide_find_mutable)(wchar_t*,wchar_t)=&std::wcschr;
long double (*power_integer)(long double,int)=&std::pow;
std::ostream& (std::ostream::*address_output)(const void*)=&std::ostream::operator<<;
std::locale (std::ios_base::*imbue)(const std::locale&)=&std::ios_base::imbue;
const std::ctype<char>& facet=std::use_facet<std::ctype<char> >(std::locale::classic());
std::basic_stringstream<char,std::char_traits<char>,std::allocator<char> > stream;
std::streampos pos; std::mbstate_t state; std::fpos<std::mbstate_t> file_pos;
}
int main(){stream<<std::setw(4)<<std::setfill('0')<<3;return stream.str()=="0003"?0:1;}

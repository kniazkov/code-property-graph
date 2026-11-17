#include <iosfwd>
std::istreambuf_iterator<char>* input;
std::ostreambuf_iterator<wchar_t>* output;
std::char_traits<char>* narrow;
std::char_traits<wchar_t>* wide;
#include <iterator>
#include <string>
int main() { return std::char_traits<char>::eq('a', 'a') && std::char_traits<wchar_t>::eq(L'a', L'a') ? 0 : 1; }

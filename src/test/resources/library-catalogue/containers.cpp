#include <vector>
#include <list>
#include <deque>
#include <map>
#include <set>
#include <queue>
#include <stack>
#include <string>
#include <iterator>
#include <functional>
#include <memory>
#include <limits>
#include <complex>
#include <valarray>
#include <bitset>
#include <new>
#include <sstream>
static void portable_interfaces() {
 std::vector<bool>::reference& (std::vector<bool>::reference::*assign)(bool) = &std::vector<bool>::reference::operator=;
 void (std::vector<bool>::*flip)() = &std::vector<bool>::flip;
 void (std::list<int>::*splice)(std::list<int>::iterator,std::list<int>&) = &std::list<int>::splice;
 std::pair<std::map<int,int>::iterator,bool> (std::map<int,int>::*insert)(const std::map<int,int>::value_type&) = &std::map<int,int>::insert;
 std::multimap<int,int>::iterator (std::multimap<int,int>::*multi)(const std::multimap<int,int>::value_type&) = &std::multimap<int,int>::insert;
 std::string& (std::string::*replace)(std::string::size_type,std::string::size_type,const std::string&) = &std::string::replace;
 std::string (std::string::*substr)(std::string::size_type,std::string::size_type) const = &std::string::substr;
 bool (*eq)(const char&,const char&) = &std::char_traits<char>::eq;
 const int& (std::valarray<int>::*index)(std::size_t) const = &std::valarray<int>::operator[];
 bool (std::istreambuf_iterator<char>::*equal)(const std::istreambuf_iterator<char>&) const = &std::istreambuf_iterator<char>::equal;
 std::allocator<void>::rebind<int>::other a;
 std::binder2nd<std::less<int> > predicate = std::bind2nd(std::less<int>(), 3);
 std::binary_function<int,int,int>* base = static_cast<std::plus<int>*>(0);
 std::reverse_iterator<int*> reverse;
 std::complex<float> c(std::complex<double>(1,2));
 std::basic_stringstream<char,std::char_traits<char>,std::allocator<char> > ss;
 const int limit = std::numeric_limits<int>::digits;
 (void)assign;(void)flip;(void)splice;(void)insert;(void)multi;(void)replace;(void)substr;(void)eq;(void)index;(void)equal;(void)a;(void)predicate;(void)base;(void)reverse;(void)c;(void)ss;(void)limit;
}

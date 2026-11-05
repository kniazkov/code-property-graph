#include <bitset>
void bits(std::bitset<8>& b,const std::bitset<8>& c){b.set();b.set(1);b.set(1,false);b.reset();b.reset(1);b.flip();b.flip(1);b.to_ulong();b.to_string<char,std::char_traits<char>,std::allocator<char> >();b.count();b.size();b.test(1);b.any();b.none();}

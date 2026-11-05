#include <bitset>
void operators(std::bitset<8>& a,const std::bitset<8>& b){(void)(a&b);(void)(a|b);(void)(a^b);(void)(a==b);(void)(a!=b);a&=b;a|=b;a^=b;a<<=1;a>>=1;(void)(~b);(void)(b<<1);(void)(b>>1);}

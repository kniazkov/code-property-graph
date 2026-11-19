#include <exception>
int g=0;
void put(int&a,int&b){a=3;b=7;}
void h(){put(g,g);throw g;}
void f()throw(int){std::set_unexpected(h);g=1;throw true;}
int main(){try{f();}catch(int n){return n==7&&g==7?0:9;}return 8;}

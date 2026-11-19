#include <exception>
int g=0;
struct S{~S(){g=9;}};
void h(){S s;g=7;throw g;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){try{f();}catch(int n){return n==7&&g==9?0:9;}return 8;}

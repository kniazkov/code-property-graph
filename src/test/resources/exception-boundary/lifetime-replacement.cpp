#include <exception>
#include <cstdlib>
int cleaned=0;
struct S{~S(){++cleaned;}};
void h(){S s;throw 7;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){try{f();}catch(int n){return n==7&&cleaned==1?0:9;}return 8;}

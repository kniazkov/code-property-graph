#include <exception>
#include <cstdlib>
int state=0;
struct S{S(){state=1;throw 7;}~S(){state=9;}};
void h(){S s;throw true;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){try{f();}catch(int n){return n==7&&state==1?0:9;}return 8;}

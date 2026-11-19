#include <exception>
#include <cstdlib>
int cleaned=0;
struct S{~S(){++cleaned;throw 9;}};
void stop(){std::exit(cleaned==1?0:9);}
void h(){S s;throw 7;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){std::set_terminate(stop);f();return 8;}

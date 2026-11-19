#include <exception>
#include <cstdlib>
int trace=0;
struct A{~A(){trace=1;throw 2;}};
struct S{A a;~S(){throw 7;}};
void stop(){std::exit(trace==1?0:9);}
void h(){{S s;}throw true;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){std::set_terminate(stop);f();return 8;}

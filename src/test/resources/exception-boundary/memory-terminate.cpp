#include <exception>
#include <cstdlib>
int g=0;
void stop(){std::exit(g==7?0:9);}
void h(){g=7;std::terminate();}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){std::set_terminate(stop);f();return 8;}

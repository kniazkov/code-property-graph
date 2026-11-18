#include <exception>
#include <cstdlib>
void stop(){std::exit(42);}
void target(){}
void h(){throw &target;}
void f()throw(void*){std::set_unexpected(h);throw 1;}
int main(){std::set_terminate(stop);try{f();}catch(...){return 9;}return 8;}

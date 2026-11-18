#include <exception>
#include <cstdlib>
void stop(){std::exit(42);}
void handler(){throw 7;}
void f()throw(){throw 1.0;}
int main(){std::set_terminate(stop);std::set_unexpected(handler);try{f();}catch(...){return 9;}return 8;}

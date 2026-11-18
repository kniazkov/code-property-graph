#include <exception>
#include <cstdlib>
void stop(){std::exit(42);}
void h(){throw 7;}
void f()throw(int,std::bad_exception){std::set_unexpected(h);throw 1.0;}
int main(){std::set_terminate(stop);try{f();}catch(int n){return n==7?0:9;}catch(...){return 9;}return 8;}

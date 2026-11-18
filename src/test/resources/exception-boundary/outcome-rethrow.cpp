#include <exception>
#include <cstdlib>
void stop(){std::exit(42);}
void h(){try{throw 7;}catch(int){throw;}}
void f()throw(int){std::set_unexpected(h);throw 1.0;}
int main(){std::set_terminate(stop);try{f();}catch(int n){return n==7?0:9;}catch(...){return 9;}return 8;}

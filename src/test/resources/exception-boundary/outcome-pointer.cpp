#include <exception>
#include <cstdlib>
void stop(){std::exit(42);}
struct B{};struct D:public B{};D* p=0;void h(){throw p;}
void f()throw(B*){std::set_unexpected(h);throw 1.0;}
int main(){std::set_terminate(stop);try{f();}catch(B* p){return p==0?0:9;}catch(...){return 9;}return 8;}

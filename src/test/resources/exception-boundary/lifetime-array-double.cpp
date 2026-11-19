#include <exception>
#include <cstdlib>
int made=0,dead=0;
struct S{S(){++made;if(made==3)throw 7;}~S(){++dead;throw 9;}};
void stop(){std::exit(made==3&&dead==1?0:9);}
void h(){S s[4];throw 7;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){std::set_terminate(stop);f();return 8;}

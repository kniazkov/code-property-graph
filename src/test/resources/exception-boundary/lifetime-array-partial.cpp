#include <exception>
#include <cstdlib>
int made=0,dead=0;
struct S{S(){++made;if(made==3)throw 7;}~S(){++dead;}};
void h(){S s[4];throw 7;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){try{f();}catch(int n){return n==7&&made==3&&dead==2?0:9;}return 8;}

#include <exception>
#include <cstdlib>
int made=0,dead=0,base=0;
struct B{~B(){base=1;}};
struct A{A(){++made;if(made==3)throw 7;}~A(){++dead;}};
struct S:B{A a[4];~S(){dead=9;}};
void h(){S s;throw 7;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){try{f();}catch(int n){return n==7&&made==3&&dead==2&&base==1?0:9;}return 8;}

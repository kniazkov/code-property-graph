#include <exception>
#include <cstdlib>
int trace=0;
struct A{A(){trace=trace*10+1;}~A(){trace=trace*10+4;}};
struct B{B(){trace=trace*10+2;}~B(){trace=trace*10+3;}};
struct S:A{B b;};
void h(){S s;throw 7;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){try{f();}catch(int n){return n==7&&trace==1234?0:9;}return 8;}

#include <exception>
#include <cstdlib>
int trace=0;
struct A{~A(){trace=trace*10+1;}};
struct B{~B(){trace=trace*10+2;}};
struct S:A{B b;S(){throw 7;}~S(){trace=9;}};
void h(){S s;throw true;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){try{f();}catch(int n){return n==7&&trace==21?0:9;}return 8;}

#include <exception>
#include <cstdlib>
int trace=0;
struct A{~A(){trace=trace*10+1;}};
struct B{~B(){trace=trace*10+2;}};
struct C{~C(){trace=trace*10+3;}};
struct S:A,B{C c;~S(){trace=trace*10+4;}};
void h(){S s;throw 7;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){try{f();}catch(int n){return n==7&&trace==4321?0:9;}return 8;}

#include <exception>
#include <cstdlib>
int trace=0;
struct A{~A(){trace=trace*10+1;}};
struct Inner{A a;~Inner(){trace=trace*10+2;}};
struct Outer{Inner inner;~Outer(){trace=trace*10+3;}};
void h(){Outer object;throw 7;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){try{f();}catch(int n){return n==7&&trace==321?0:9;}return 8;}

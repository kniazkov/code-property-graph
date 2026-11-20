#include <exception>
int trace=0;struct A{A(){trace=trace*10+1;}};struct B{B(){trace=trace*10+2;}};struct S:A{B b;S():b(),A(){}};void h(){S s;throw trace;}void f()throw(int){std::set_unexpected(h);throw true;}int main(){try{f();}catch(int n){return n==12?0:9;}return 8;}

#include <exception>
int made=0;int fail(){throw 7;}int stop(){++made;return 1;}struct A{A(int n){}};struct B{B(int n){}};struct S:A,B{S():B(stop()),A(fail()){}};void h(){S s;throw true;}void f()throw(int){std::set_unexpected(h);throw true;}int main(){try{f();}catch(int n){return n==7&&made==0?0:9;}return 8;}

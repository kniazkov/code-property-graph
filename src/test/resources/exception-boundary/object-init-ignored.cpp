#include <exception>
int calls=0;int fail(){++calls;throw true;}struct V{V(){}V(int){}};struct B:virtual V{B():V(fail()){}};struct D:B{};void h(){D d;throw 7;}void f()throw(int){std::set_unexpected(h);throw true;}int main(){try{f();}catch(int n){return n==7&&calls==0?0:9;}return 8;}

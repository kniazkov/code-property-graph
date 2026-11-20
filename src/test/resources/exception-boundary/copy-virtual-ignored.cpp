#include <exception>
int calls=0;struct V{V(){}V(const V& v){++calls;}};V original;struct B:virtual V{B():V(original){}};struct S:B{S(){throw 7;}};void h(){S s;throw true;}void f()throw(int){std::set_unexpected(h);throw true;}int main(){try{f();}catch(int n){return n==7&&calls==0?0:9;}return 8;}

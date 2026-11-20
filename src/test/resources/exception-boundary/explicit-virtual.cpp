#include <exception>
int made=0;int stop(){++made;return 1;}struct V{V(int n){}};struct A:virtual V{A():V(stop()){}};struct S:A{S():V(1){throw 7;}};void h(){S s;throw true;}void f()throw(int){std::set_unexpected(h);throw true;}int main(){try{f();}catch(int n){return n==7&&made==0?0:9;}return 8;}

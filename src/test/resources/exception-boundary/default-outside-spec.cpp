#include <exception>
int fail(){throw 7;}struct S{S(int n=fail())throw(){}};void h(){S s;throw true;}void f()throw(int){std::set_unexpected(h);throw true;}int main(){try{f();}catch(int n){return n==7?0:9;}return 8;}

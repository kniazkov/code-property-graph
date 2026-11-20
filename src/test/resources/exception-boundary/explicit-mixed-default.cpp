#include <exception>
int made=0;int fail(){throw 7;}struct S{S(int n,int m=fail())throw(){++made;}};void h(){S s(1);throw true;}void f()throw(int){std::set_unexpected(h);throw true;}int main(){try{f();}catch(int n){return n==7&&made==0?0:9;}return 8;}

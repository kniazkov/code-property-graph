#include <exception>
int made=0;int fail(){throw 7;}struct A{A(){++made;}};struct S:A{S(int n=fail()){}~S(){made=9;}};void h(){S s;throw true;}void f()throw(int){std::set_unexpected(h);throw true;}int main(){try{f();}catch(int n){return n==7&&made==0?0:9;}return 8;}

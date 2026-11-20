#include <exception>
int dead=0;int fail(){throw 7;}struct A{~A(){++dead;}};struct S{A a;int x;S():x(fail()){}~S(){dead=9;}};void h(){S s;throw true;}void f()throw(int){std::set_unexpected(h);throw true;}int main(){try{f();}catch(int n){return n==7&&dead==1?0:9;}return 8;}

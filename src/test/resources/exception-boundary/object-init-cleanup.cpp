#include <exception>
int dead=0;struct A{~A(){++dead;}};struct B{B(){throw 7;}};struct S:A{B b;S():A(),b(){}~S(){dead=9;}};void h(){S s;throw true;}void f()throw(int){std::set_unexpected(h);throw true;}int main(){try{f();}catch(int n){return n==7&&dead==1?0:9;}return 8;}

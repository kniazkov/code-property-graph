#include <exception>
int calls=0,dead=0;int arg(){if(++calls==3)throw 7;return 0;}struct A{A(int n){}~A(){++dead;}};struct S{A a;S():a(arg()){}};void h(){S s[4];throw true;}void f()throw(int){std::set_unexpected(h);throw true;}int main(){try{f();}catch(int n){return n==7&&calls==3&&dead==2?0:9;}return 8;}

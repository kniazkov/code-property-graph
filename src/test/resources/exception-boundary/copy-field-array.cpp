#include <exception>
int calls=0,dead=0;struct B{B(){}B(const B& b){if(++calls==3)throw 7;}~B(){++dead;}};B original;struct S{B b;S():b(original){}};void h(){S s[4];throw true;}void f()throw(int){std::set_unexpected(h);throw true;}int main(){try{f();}catch(int n){return n==7&&calls==3&&dead==2?0:9;}return 8;}

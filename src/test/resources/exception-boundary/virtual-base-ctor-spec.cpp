#include <exception>
struct V{V(){throw 7;}};
struct L:virtual V{L()throw(){}};
struct D:L{};
void h(){D d;throw true;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){try{f();}catch(int n){return n==7?0:9;}return 8;}

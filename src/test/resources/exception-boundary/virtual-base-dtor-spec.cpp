#include <exception>
int count=0;
struct V{~V(){++count;throw 7;}};
struct L:virtual V{~L()throw(){}};
struct D:L{};
void h(){{D d;}throw true;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){try{f();}catch(int n){return n==7&&count==1?0:9;}return 8;}

#include <exception>
#include <cstdlib>
struct S{~S(){throw 7;}};
void helper(){S s;return;}
void h(){helper();throw true;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){try{f();}catch(int n){return n==7?0:9;}return 8;}

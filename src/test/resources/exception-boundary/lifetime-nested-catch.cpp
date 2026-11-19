#include <exception>
#include <cstdlib>
struct S{~S(){throw 2.0;}};
void h(){try{S s;}catch(double){throw 7;}}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){try{f();}catch(int n){return n==7?0:9;}return 8;}

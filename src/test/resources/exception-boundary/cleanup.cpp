#include <exception>
void first(){throw 1;}
void second(){throw 2;}
struct Guard{Guard()throw(){}~Guard()throw(){std::set_unexpected(second);}};
void f()throw(int){Guard g;std::set_unexpected(first);throw 1.0;}
int main(){try{f();}catch(int n){return n==1?0:9;}return 8;}

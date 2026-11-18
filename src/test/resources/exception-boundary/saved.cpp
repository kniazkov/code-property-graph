#include <exception>
void first(){throw 1;}
void second(){throw 2;}
void f()throw(int){std::set_unexpected(first);std::unexpected_handler old=std::set_unexpected(second);std::unexpected_handler copy=old;old=second;std::set_unexpected(copy);throw 1.0;}
int main(){try{f();}catch(int n){return n==1?0:9;}return 8;}

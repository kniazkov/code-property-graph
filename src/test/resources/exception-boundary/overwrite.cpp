#include <exception>
void first(){throw 1;}
void second(){throw 2;}
void f(bool b)throw(int){std::set_unexpected(first);if(b)std::set_unexpected(&second);throw 1.0;}
int main(){try{f(true);}catch(int n){return n==2?0:9;}return 8;}

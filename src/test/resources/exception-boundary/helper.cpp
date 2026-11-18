#include <exception>
void first(){throw 1;}
void second(){throw 2;}
std::unexpected_handler exchange(std::unexpected_handler p)throw();
void install(std::unexpected_handler p)throw(){exchange(p);}
void f()throw(int){install(first);std::unexpected_handler old=exchange(second);install(old);throw 1.0;}
std::unexpected_handler exchange(std::unexpected_handler p)throw(){return std::set_unexpected(p);}
int main(){try{f();}catch(int n){return n==1?0:9;}return 8;}

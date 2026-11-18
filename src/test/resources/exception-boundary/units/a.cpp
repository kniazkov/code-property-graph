#include <exception>
std::unexpected_handler exchange(std::unexpected_handler p)throw();
void first(){throw 1;}
void second(){throw 2;}
void f()throw(int){exchange(first);std::unexpected_handler old=exchange(second);exchange(old);throw 1.0;}
int main(){try{f();}catch(int n){return n==1?0:9;}return 8;}

#include <exception>
void first(){throw 1;}
void second(){throw 2;}
void temporary()throw(){std::unexpected_handler old=std::set_unexpected(second);std::set_unexpected(old);}
void f()throw(int){std::set_unexpected(first);temporary();throw 1.0;}
int main(){try{f();}catch(int n){return n==1?0:9;}return 8;}

#include <exception>
int effect=0;
void handler(){effect=7;throw 7;}
void f()throw(int){std::set_unexpected(handler);throw 1.0;}
int main(){try{f();}catch(int n){return n==7&&effect==7?0:9;}return 8;}

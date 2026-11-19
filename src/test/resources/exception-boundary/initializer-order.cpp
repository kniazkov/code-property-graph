#include <exception>
int trace=0;int first(){trace=trace*10+1;return 1;}int second(){trace=trace*10+2;return 2;}struct S{int x;int y;S():y(second()),x(first()){}};void h(){S s;throw trace;}void f()throw(int){std::set_unexpected(h);throw true;}int main(){try{f();}catch(int n){return n==12?0:9;}return 8;}

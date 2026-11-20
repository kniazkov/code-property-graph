#include <exception>
int selected=0;struct S{S(){}S(S& s){selected=1;throw 7;}S(const S& s){selected=2;throw 7;}};void h(){const S a;S b=a;throw true;}void f()throw(int){std::set_unexpected(h);throw true;}int main(){try{f();}catch(int n){return n==7&&selected==2?0:9;}return 8;}

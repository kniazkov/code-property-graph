#include <exception>
int dead=0;int fail(){throw 7;}struct S{S(){}S(const S& s,int n=fail())throw(){}~S(){++dead;}};void h(){S a;S b=a;throw true;}void f()throw(int){std::set_unexpected(h);throw true;}int main(){try{f();}catch(int n){return n==7&&dead==1?0:9;}return 8;}

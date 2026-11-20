#include <exception>
struct S;void helper(const S& s){throw 7;}struct S{S(){}S(const S& s){helper(s);}};void h(){S a;S b=a;throw true;}void f()throw(int){std::set_unexpected(h);throw true;}int main(){try{f();}catch(int n){return n==7&&true?0:9;}return 8;}

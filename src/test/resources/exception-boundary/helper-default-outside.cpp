#include <exception>
int calls=0;int fail(){throw 7;}void helper(int n=fail())throw(){++calls;}void h(){helper();throw true;}void f()throw(int){std::set_unexpected(h);throw true;}int main(){try{f();}catch(int n){return n==7&&calls==0?0:9;}return 8;}

#include <functional>
int unary(int x){return x;} int binary(int x,int y){return x+y;}
struct C {int get(){return 1;} int add(int x){return x;} int cget()const{return 1;} int cadd(int x)const{return x;}};
void adapters(){std::not1(std::logical_not<int>());std::not2(std::less<int>());std::bind1st(std::plus<int>(),1);std::bind2nd(std::plus<int>(),1);std::ptr_fun(unary);std::ptr_fun(binary);std::mem_fun(&C::get);std::mem_fun(&C::add);std::mem_fun(&C::cget);std::mem_fun(&C::cadd);std::mem_fun_ref(&C::get);std::mem_fun_ref(&C::add);std::mem_fun_ref(&C::cget);std::mem_fun_ref(&C::cadd);}

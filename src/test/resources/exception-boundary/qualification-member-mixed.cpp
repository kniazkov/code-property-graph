struct A{int x;};
void f()throw(const int A::*const*){int A::** p=0;throw p;}
int main(){try{f();}catch(const int A::*const* p){return p==0?0:9;}catch(...){return 7;}return 8;}

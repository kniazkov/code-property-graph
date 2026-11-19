struct A{int x;};
void f()throw(int A::*){throw &A::x;}
int main(){try{f();}catch(int A::* p){A a;a.x=7;return a.*p==7?0:9;}catch(...){return 7;}return 8;}

#include <exception>
int trace=0;
struct A{A(){trace=1;}~A(){trace=trace*10+2;}};
struct B{B(){throw 7;}~B(){trace=9;}};
struct D:virtual A,virtual B{~D(){trace=8;}};
void h(){D d;throw true;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){try{f();}catch(int n){return n==7&&trace==12?0:9;}return 8;}

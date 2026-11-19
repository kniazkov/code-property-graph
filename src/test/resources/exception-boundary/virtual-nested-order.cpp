#include <exception>
int trace=0;
struct A{A(){trace=trace*10+1;}~A(){trace=trace*10+8;}};
struct B:virtual A{B(){trace=trace*10+2;}~B(){trace=trace*10+7;}};
struct L:virtual B{L(){trace=trace*10+3;}~L(){trace=trace*10+6;}};
struct R:virtual B{R(){trace=trace*10+4;}~R(){trace=trace*10+5;}};
struct D:L,R{};
void h(){D d;throw 7;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){try{f();}catch(int n){return n==7&&trace==12345678?0:9;}return 8;}

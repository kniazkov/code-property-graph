#include <exception>
int trace=0;
struct V{V(){trace=trace*10+1;}~V(){trace=trace*10+8;}};
struct L:virtual V{L(){trace=trace*10+2;}~L(){trace=trace*10+7;}};
struct R:virtual V{R(){trace=trace*10+3;}~R(){trace=trace*10+6;}};
struct D:L,R{D(){trace=trace*10+4;}~D(){trace=trace*10+5;}};
void h(){D d;throw 7;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){try{f();}catch(int n){return n==7&&trace==12345678?0:9;}return 8;}

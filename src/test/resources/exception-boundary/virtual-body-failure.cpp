#include <exception>
int trace=0;
struct V{~V(){trace=trace*10+1;}};
struct L:virtual V{~L(){trace=trace*10+2;}};
struct D:L{D(){throw 7;}~D(){trace=9;}};
void h(){D d;throw true;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){try{f();}catch(int n){return n==7&&trace==21?0:9;}return 8;}

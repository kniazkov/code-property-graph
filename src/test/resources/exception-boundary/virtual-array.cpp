#include <exception>
int made=0,dead=0;
struct V{V(){++made;}~V(){++dead;}};
struct L:virtual V{};struct R:virtual V{};struct D:L,R{};
void h(){D objects[2];throw 7;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){try{f();}catch(int n){return n==7&&made==2&&dead==2?0:9;}return 8;}

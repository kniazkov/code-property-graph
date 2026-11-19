#include <exception>
int made=0,dead=0;
struct V{V(){++made;}~V(){++dead;}};
struct L:virtual V{};
struct D:virtual V{L member;};
void h(){D d;throw 7;}
void f()throw(int){std::set_unexpected(h);throw true;}
int main(){try{f();}catch(int n){return n==7&&made==2&&dead==2?0:9;}return 8;}

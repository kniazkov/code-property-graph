#include <utility>
struct X {int n;}; bool operator==(const X& a,const X& b){return a.n==b.n;} bool operator<(const X& a,const X& b){return a.n<b.n;}
bool operator!=(const X&,const X&){return false;}using namespace std::rel_ops;int main(){X a={1},b={2};return (a!=b)?1:0;}

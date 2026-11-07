#include <utility>
struct X {int n;}; bool operator==(const X& a,const X& b){return a.n==b.n;} bool operator<(const X& a,const X& b){return a.n<b.n;}
using std::rel_ops::operator<=;int main(){X a={1},b={2};return (a<=b)?0:1;}

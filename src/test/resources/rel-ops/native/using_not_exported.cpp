#include <utility>
struct X {int n;}; bool operator==(const X& a,const X& b){return a.n==b.n;} bool operator<(const X& a,const X& b){return a.n<b.n;}
int f(){using namespace std::rel_ops;X a={1},b={2};return a!=b;} int main(){X a={1},b={2};return a!=b;}

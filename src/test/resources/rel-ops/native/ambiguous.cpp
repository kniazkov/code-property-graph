#include <utility>
struct X {int n;}; bool operator==(const X& a,const X& b){return a.n==b.n;} bool operator<(const X& a,const X& b){return a.n<b.n;}
namespace custom {template<class T> bool operator!=(const T&,const T&){return true;}} using namespace custom;using namespace std::rel_ops;int main(){X a={1},b={2};return a!=b;}

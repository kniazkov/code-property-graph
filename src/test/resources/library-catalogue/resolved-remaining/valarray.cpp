#include <valarray>
int unary(int x){return x;}int byref(const int& x){return x;}
void arrays(std::valarray<int>& v){v.size();v.sum();v.min();v.max();v.shift(1);v.cshift(1);v.apply(unary);v.apply(byref);v.resize(3);v.resize(3,1);}

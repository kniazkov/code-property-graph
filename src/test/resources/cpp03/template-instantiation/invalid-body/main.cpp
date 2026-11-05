template<class T> int inner(const T& x) { return x.size(); }
template<class T> int outer(const T& x) { return inner(x); }
int main() { return outer(1); }

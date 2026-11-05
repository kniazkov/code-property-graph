template<class A, class B> struct Pair { static int value() { return 0; } };
template<class A, class B> struct Pair<A*, B> { static int value() { return 1; } };
template<class A, class B> struct Pair<A, B*> { static int value() { return 2; } };
int main() { return Pair<int*, int*>::value(); }

template<class T> int value(T x) { return 1; }
template<class T> int value<T*>(T* x) { return 2; }
int main() { int x = 0; return value(&x); }

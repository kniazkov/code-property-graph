template<class T> int identity(T);
#include <typeinfo>
template<class U> int identity(U value) { typeid(U); return value; }
template int identity<int>(int);
int main() { return identity(7) == 7 ? 0 : 1; }

#include <vector>
template<class T> int count(const T& x) { return x.size(); }
template<class T> int neverInstantiated(const T& x) { return x.size(); }
template<class T> struct Identity { typedef T value_type; T value; };
template<class T = int, int N = 3> struct Buffer { T values[N]; };
template<template<class> class Holder = Identity, class T = int>
struct Envelope { Holder<T> data; };
void nestedArguments(Buffer<Buffer<int> > value);
void defaultArguments(Envelope<> value, Buffer<int> slots);
template<class T> typename T::value_type dependentValue(const T& x) { return x.value; }
template<class T> int dependentTemplate(const T& x) { return x.template convert<int>(); }
int main() {
    std::vector<int> values(3);
    return count(values) - 3;
}

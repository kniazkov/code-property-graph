#ifndef TEMPLATE_API_H
#define TEMPLATE_API_H
struct Tagged { typedef int tag; int value; };
template<class T> typename T::tag choose(const T& x) { return x.value; }
int choose(...);
template<class T> int inner(const T& x) { return x.size(); }
template<class T> int outer(const T& x) { return inner(x); }
template<class T> T identity(T x) { return x; }
template<class T> int classify(T x) { return 1; }
template<class T> int classify(T* x) { return 2; }
template<class T> struct Select { static int value() { return 1; } };
template<class T> struct Select<T*> { static int value() { return 2; } };
template<class T> struct Select<const T*> { static int value() { return 4; } };
template<> struct Select<int> { static int value() { return 3; } };
template<class T> int specialized(T x) { return 1; }
template<> inline int specialized<int>(int x) { return 9; }
template<int N> int length(const int (&x)[N]) { return N; }
template<int N> int fixedValue() { return N; }
template<class T> struct Box { T value; };
template<template<class> class C, class T> T readBox(const C<T>& x) { return x.value; }
int other();
#endif

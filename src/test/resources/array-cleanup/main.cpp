#include <cstdio>
#include <cstdlib>
#include <new>
void* operator new[](std::size_t size) throw(std::bad_alloc) {
    void* p = std::malloc(size);
    if (!p) throw std::bad_alloc();
    return p;
}
void operator delete[](void* p) throw() {
    std::printf("D ");
    std::free(p);
}
int attempted = 0;
int failAt = 0;
struct Guard {
    int id;
    Guard() : id(++attempted) {
        std::printf("+%d ", id);
        if (id == failAt) throw id;
    }
    ~Guard() throw() { std::printf("-%d ", id); }
};
struct Sentinel { ~Sentinel() throw() { std::printf("S "); } };
struct Owner {
    Guard first;
    Guard items[3];
    Guard last;
    Owner() try {} catch (...) { std::printf("H "); throw; }
    ~Owner() throw() {}
};
void automaticArray() {
    try { Sentinel sentinel; Guard items[3]; }
    catch (...) { std::printf("catch "); }
}
void dynamicArray() {
    try { Sentinel sentinel; Guard* items = new Guard[3]; delete[] items; }
    catch (...) { std::printf("catch "); }
}
void memberArray() {
    try { Sentinel sentinel; Owner owner; }
    catch (...) { std::printf("catch "); }
}
int main() {
    for (int failure = 0; failure <= 3; ++failure) {
        attempted = 0; failAt = failure;
        std::printf("A%d: ", failure); automaticArray(); std::printf("\n");
        attempted = 0; failAt = failure;
        std::printf("N%d: ", failure); dynamicArray(); std::printf("\n");
        attempted = 0; failAt = failure == 0 ? 0 : failure + 1;
        std::printf("M%d: ", failure); memberArray(); std::printf("\n");
    }
    return 0;
}
